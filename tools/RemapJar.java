/* Build-time class-file transformer. It never loads classes from its input JARs. */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

public final class RemapJar {
    private static final class Names extends Remapper {
        final Map<String,String> classes = new HashMap<>();
        final Map<String,String> fields = new HashMap<>();
        final Map<String,String> methods = new HashMap<>();
        final Map<String,List<String>> parents = new HashMap<>();
        Names(Path mappings, boolean reverse) throws IOException {
            List<String[]> lines = new ArrayList<>();
            for (String line : Files.readAllLines(mappings, StandardCharsets.UTF_8)) {
                if (!line.isEmpty()) lines.add(line.split("\t"));
            }
            Map<String,String> forward = new HashMap<>();
            for (String[] p : lines) if (p[0].equals("CL")) {
                forward.put(p[1],p[2]);
                classes.put(reverse ? p[2] : p[1], reverse ? p[1] : p[2]);
            }
            Remapper descriptorMapper = new Remapper() {
                public String map(String type) { return forward.getOrDefault(type,type); }
            };
            for (String[] p : lines) {
                if (p[0].equals("FD")) {
                    String owner = reverse ? forward.getOrDefault(p[1],p[1]) : p[1];
                    fields.put(owner+'.'+(reverse?p[3]:p[2]), reverse?p[2]:p[3]);
                } else if (p[0].equals("MD")) {
                    String owner = reverse ? forward.getOrDefault(p[1],p[1]) : p[1];
                    String desc = reverse ? descriptorMapper.mapMethodDesc(p[3]) : p[3];
                    methods.put(owner+'.'+(reverse?p[4]:p[2])+desc,reverse?p[2]:p[4]);
                }
            }
        }
        public String map(String type) { return classes.getOrDefault(type,type); }
        String lookup(Map<String,String> table,String owner,String suffix) {
            Set<String> seen = new HashSet<>();
            Deque<String> todo = new ArrayDeque<>();
            todo.add(owner);
            while (!todo.isEmpty()) {
                String cur=todo.removeFirst();
                if (!seen.add(cur)) continue;
                String result=table.get(cur+'.'+suffix);
                if (result!=null) return result;
                todo.addAll(parents.getOrDefault(cur,Collections.emptyList()));
            }
            return null;
        }
        public String mapFieldName(String owner,String name,String desc) {
            String result=lookup(fields,owner,name);
            return result==null?name:result;
        }
        public String mapMethodName(String owner,String name,String desc) {
            if(name.startsWith("<")) return name;
            String result=lookup(methods,owner,name+desc);
            return result==null?name:result;
        }
        void hierarchy(Path jar) throws IOException {
            try(JarFile input=new JarFile(jar.toFile())) {
                Enumeration<JarEntry> entries=input.entries();
                while(entries.hasMoreElements()) {
                    JarEntry entry=entries.nextElement();
                    if(!entry.getName().endsWith(".class")) continue;
                    try(InputStream stream=input.getInputStream(entry)) {
                        ClassReader reader=new ClassReader(stream);
                        List<String> p=new ArrayList<>();
                        if(reader.getSuperName()!=null) p.add(reader.getSuperName());
                        p.addAll(Arrays.asList(reader.getInterfaces()));
                        parents.put(reader.getClassName(),p);
                    }
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length<4) throw new IllegalArgumentException("Usage: RemapJar mappings forward|reverse input.jar output.jar [hierarchy.jar ...] [--publicize] [--mcp-exceptions]");
        Names names=new Names(Paths.get(args[0]),args[1].equals("reverse"));
        names.hierarchy(Paths.get(args[2]));
        boolean publicize=false;
        boolean mcpExceptions=false;
        for(int i=4;i<args.length;i++) {
            if(args[i].equals("--publicize")) publicize=true;
            else if(args[i].equals("--mcp-exceptions")) mcpExceptions=true;
            else names.hierarchy(Paths.get(args[i]));
        }
        final boolean expose=publicize;
        final boolean mcp=mcpExceptions;
        Path outputPath=Paths.get(args[3]);
        if(outputPath.getParent()!=null) Files.createDirectories(outputPath.getParent());
        int transformed=0;
        try(JarFile input=new JarFile(args[2]); JarOutputStream output=new JarOutputStream(Files.newOutputStream(outputPath))) {
            List<JarEntry> entries=Collections.list(input.entries());
            entries.sort(Comparator.comparing(JarEntry::getName));
            Set<String> written=new HashSet<>();
            for(JarEntry entry:entries) {
                String oldName=entry.getName();
                if(entry.isDirectory() || oldName.equals("META-INF/MANIFEST.MF") || oldName.matches("META-INF/.*\\.(SF|RSA|DSA)")) continue;
                byte[] bytes;
                try(InputStream stream=input.getInputStream(entry)) { bytes=readAll(stream); }
                String newName=oldName;
                if(oldName.endsWith(".class")) {
                    ClassReader reader=new ClassReader(bytes);
                    ClassWriter writer=new ClassWriter(0);
                    ClassVisitor target=writer;
                    if(expose || mcp) target=new ClassVisitor(Opcodes.ASM9,target) {
                        String currentClass;
                        int publicAccess(int access) { return expose ? (access & ~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC : access; }
                        public void visit(int version,int access,String name,String signature,String superName,String[] interfaces) {
                            currentClass=name;
                            super.visit(version,publicAccess(access),name,signature,superName,interfaces);
                        }
                        public void visitInnerClass(String name,String outer,String inner,int access) {
                            super.visitInnerClass(name,outer,inner,publicAccess(access));
                        }
                        public FieldVisitor visitField(int access,String name,String desc,String signature,Object value) {
                            return super.visitField(publicAccess(access),name,desc,signature,value);
                        }
                        public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                            // MCP's development API declares these checked exceptions;
                            // Mojang's shipping bytecode omits the metadata. Compile only.
                            if(mcp && currentClass.equals("net/minecraft/client/gui/GuiScreen") &&
                               ((name.equals("actionPerformed") && desc.equals("(Lnet/minecraft/client/gui/GuiButton;)V")) ||
                                (name.equals("mouseClicked") && desc.equals("(III)V")) ||
                                (name.equals("keyTyped") && desc.equals("(CI)V")) ||
                                ((name.equals("handleMouseInput") || name.equals("handleKeyboardInput")) && desc.equals("()V")))) {
                                Set<String> declared=new LinkedHashSet<>();
                                if(exceptions!=null) declared.addAll(Arrays.asList(exceptions));
                                declared.add("java/io/IOException");
                                exceptions=declared.toArray(new String[0]);
                            }
                            return super.visitMethod(publicAccess(access),name,desc,signature,exceptions);
                        }
                    };
                    ClassRemapper classRemapper=new ClassRemapper(target,names) {
                        protected MethodVisitor createMethodRemapper(MethodVisitor visitor) {
                            return new MethodRemapper(visitor,names) {
                                public void visitInvokeDynamicInsn(String name,String descriptor,Handle bootstrap,Object... arguments) {
                                    // The invokedynamic name is the SAM method name for
                                    // Java lambdas; ordinary Remapper cannot infer its
                                    // descriptor without the bootstrap arguments.
                                    if(bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory") &&
                                       arguments.length>0 && arguments[0] instanceof Type) {
                                        Type functional=Type.getReturnType(descriptor);
                                        if(functional.getSort()==Type.OBJECT) {
                                            name=names.mapMethodName(functional.getInternalName(),name,((Type)arguments[0]).getDescriptor());
                                        }
                                    }
                                    super.visitInvokeDynamicInsn(name,descriptor,bootstrap,arguments);
                                }
                            };
                        }
                    };
                    reader.accept(classRemapper,0);
                    bytes=writer.toByteArray();
                    newName=names.mapType(reader.getClassName())+".class";
                    transformed++;
                }
                if(!written.add(newName)) throw new IOException("Duplicate output entry "+newName);
                JarEntry result=new JarEntry(newName);
                result.setTime(315532800000L);
                output.putNextEntry(result);
                output.write(bytes);
                output.closeEntry();
            }
        }
        System.out.println("Statically transformed "+transformed+" class files into "+outputPath);
    }
    static byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while((count=stream.read(buffer))!=-1) output.write(buffer,0,count);
        return output.toByteArray();
    }
}
