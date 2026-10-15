"""Read-only source checks for the gameplay/UI fixes. This does not compile or run Java."""

import json
import math
import re
import sys
from pathlib import Path

FILES = [
    "dev/slinky/client/BindingInput.java",
    "dev/slinky/client/InputQuery.java",
    "dev/slinky/client/InputRouter.java",
    "dev/slinky/client/ClientEventBridge.java",
    "dev/slinky/client/hook/GameHooks.java",
    "dev/slinky/client/hook/UiFrameDriver.java",
    "dev/slinky/client/hook/ForgeUiSurface.java",
    "dev/slinky/client/inventory/ItemActionHelper.java",
    "dev/slinky/client/placement/PlacementSelection.java",
    "dev/slinky/client/render/OpenB4GuiScreen.java",
    "dev/slinky/client/render/OpenB4Theme.java",
    "dev/slinky/client/render/NotificationQueue.java",
    "dev/slinky/client/render/RecoveredGuiScreen.java",
    "dev/slinky/module/ModuleStateQueue.java",
    "dev/slinky/module/ModuleManager.java",
    "dev/slinky/module/impl/FreeLookModule.java",
    "dev/slinky/module/impl/AutoBlockModule.java",
    "dev/slinky/module/impl/AutoRodModule.java",
    "dev/slinky/module/impl/AutoPotModule.java",
    "dev/slinky/module/impl/AutoSoupModule.java",
    "dev/slinky/module/impl/BlockInModule.java",
    "dev/slinky/module/impl/BridgeAssistModule.java",
    "dev/slinky/module/impl/ClutchModule.java",
    "dev/slinky/module/impl/GuiModule.java",
    "dev/slinky/module/impl/ModOverlayModule.java",
    "dev/slinky/module/impl/NotificationsModule.java",
]


def code_only(text):
    out = list(text)
    index = 0
    while index < len(text):
        start = index
        if text.startswith("//", index):
            end = text.find("\n", index)
            index = len(text) if end < 0 else end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            if end < 0:
                raise ValueError("unterminated block comment")
            index = end + 2
        elif text[index] in (chr(34), chr(39)):
            quote = text[index]
            index += 1
            while index < len(text):
                if text[index] == chr(92):
                    index += 2
                elif text[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            else:
                raise ValueError("unterminated string/character literal")
        else:
            index += 1
            continue
        for position in range(start, min(index, len(out))):
            if out[position] != "\n":
                out[position] = " "
    return "".join(out)


def check(root):
    src = root / "java/src/main/java"
    metadata = json.loads((root / "java/src/main/resources/assets/slinkysemantic/MetadataCatalog.json").read_text(encoding="utf-8-sig"))
    modules = {module["module"]: {setting["key"] for setting in module["settings"]} for module in metadata["modules"]}
    files = sorted(set(FILES) | {str(path.relative_to(src)).replace(chr(92), "/") for path in (src / "dev/slinky").rglob("*.java") if "/shaded/" not in path.as_posix()})
    errors = []
    catalog_path = "java/src/main/resources/assets/slinkysemantic/OpenB4GuiCatalog.json"
    categories = {"combat", "movement", "player", "world", "render", "network", "minigames", "other", "client", "scripts", "profiles"}
    try:
        catalog = json.loads((root / catalog_path).read_text(encoding="utf-8-sig"))
        if not isinstance(catalog, list):
            errors.append([catalog_path, "catalog root must be an array"])
        else:
            for index, record in enumerate(catalog):
                if not isinstance(record, dict):
                    errors.append([catalog_path, index, "catalog entry must be an object"])
                    continue
                name, category, lite = record.get("name"), record.get("category"), record.get("lite")
                if not isinstance(name, str) or not name.strip():
                    errors.append([catalog_path, index, "name must be a nonempty string"])
                if not isinstance(category, str) or category not in categories:
                    errors.append([catalog_path, index, "unknown or invalid category"])
                if not isinstance(lite, bool):
                    errors.append([catalog_path, index, "lite must be a boolean"])
    except (OSError, ValueError) as failure:
        errors.append([catalog_path, str(failure)])
    for relative in files:
        text = (src / relative).read_text(encoding="utf-8-sig")
        try:
            clean = code_only(text)
        except ValueError as failure:
            errors.append([relative, str(failure)])
            continue
        stack = []
        for index, char in enumerate(clean):
            if char in "({[":
                stack.append((char, index))
            elif char in ")}]":
                expected = "({["[")}]".index(char)]
                if not stack or stack[-1][0] != expected:
                    errors.append([relative, "unbalanced delimiter", text.count("\n", 0, index) + 1])
                    break
                stack.pop()
        if stack:
            errors.append([relative, "unclosed delimiter", text.count("\n", 0, stack[-1][1]) + 1])
        package = re.search(r"\bpackage\s+([\w.]+)\s*;", clean)
        if package is None or package.group(1).replace(".", "/") != Path(relative).parent.as_posix():
            errors.append([relative, "package/path mismatch"])
        for name in re.findall(r"^import\s+(dev\.slinky\.[\w.]+);", clean, re.MULTILINE):
            parts = name.split(".")
            while len(parts) > 2 and not (src / ("/".join(parts) + ".java")).is_file():
                parts.pop()
            if len(parts) <= 2:
                errors.append([relative, "unresolved local import", name])
        if re.search(r"^(<<<<<<<|=======|>>>>>>>)", text, re.MULTILINE):
            errors.append([relative, "conflict marker"])
        if "/module/impl/" in relative:
            module = re.search(r'super\("[^"]+",\s*"([^"]+)"', text)
            if module:
                keys = modules.get(module.group(1), set()) | set(re.findall(r'new\s+(?:Boolean|Choice|Number|Range|Color|StringList|NamedColorList)Setting\("([^"]+)"', text))
                used = set(re.findall(r'\b(?:flag|choice|number|range|names|color)\("([^"]+)"\)', text))
                for key in sorted(used - keys):
                    errors.append([relative, "unknown setting", key])
        if Path(relative).name in {
            "AutoRodModule.java", "AutoPotModule.java", "AutoSoupModule.java",
            "BlockInModule.java", "BridgeAssistModule.java", "ClutchModule.java",
        }:
            callback = "onClientInputTick" if Path(relative).name in {"AutoRodModule.java", "AutoPotModule.java", "AutoSoupModule.java"} else "onClientTick"
            if not re.search(r"\b" + callback + r"\s*\(", clean):
                errors.append([relative, "missing real tick callback", callback])
            if re.search(r"\b(?:type|t)\s*==\s*0\b", clean):
                errors.append([relative, "ray/render event used as an action clock"])
            if re.search(r"\bKeyBinding\.onTick\s*\(|\.press\s*\(", clean):
                errors.append([relative, "queued key action instead of synchronous interaction"])
    errors.extend(check_hud_contracts(src))
    # Restored bind-action: no special hide/disable gate may bypass the normal module paths.
    for relative in (
        "dev/slinky/module/ModuleManager.java", "dev/slinky/client/render/GuiAppearance.java",
        "dev/slinky/client/render/OpenB4GuiScreen.java", "dev/slinky/module/impl/ModOverlayModule.java",
    ):
        if '"blockin"' in (src / relative).read_text(encoding="utf-8-sig"):
            errors.append([relative, "BlockIn-specific quarantine or visibility exclusion remains"])
    if "implemented.add(new BlockInModule(manager))" not in (src / "dev/slinky/module/CoreModules.java").read_text(encoding="utf-8-sig"):
        errors.append(["CoreModules.java", "restored BlockIn implementation must remain registered"])
    return files, errors


def check_hud_contracts(src):
    """Mechanical source regression guards; never load Java or invoke a shader compiler."""
    contracts = {
        "client/render/OpenB4GuiScreen.java": [
            r"PANEL_WIDTH\s*=\s*124\.0F", r"scissorBox\s*=\s*BufferUtils\.createIntBuffer\(16\)",
            r"scissorBox\.clear\(\);\s*GL11\.glGetInteger\(GL11\.GL_SCISSOR_BOX",
            r"control\.offset < HEADER_HEIGHT \+ panel\.bodyHeight",
            r"direction = wheel > 0\.0F \? 1 : wheel < 0\.0F \? -1 : 0",
        ],
        "client/render/CanvasFonts.java": [
            r"guiFace\(float pixelsPerUnit\)", r"rasterRatio = this\.previousRatio",
            r"void release\(\)\s*\{\s*generation\+\+", r"deviceRatio\(\)",
        ],
        "client/render/RenderAccess.java": [
            r"GL11\.glGetFloat\(GL11\.GL_MODELVIEW_MATRIX, baseModel\)",
            r"pillGradient\(canvas\.selected\(\)\.transform, v, alpha, baseModel\)",
            r"buffers\.push\(baseModel\)", r"if \(!itemTransformResolved\)",
        ],
        "client/render/ScreenshotModuleList.java": [
            r"DripLiteStyle\.face\(\)", r"DripLiteStyle\.ROW", r"DripLiteStyle\.SURFACE",
            r"this\.rows\.get\(entry\.id\)", r"dt / DURATION", r"append\(row\.text\(scale, i\)\)",
            r"this\.progress \* this\.progress \* \(3 - 2 \* this\.progress\)",
            r"this\.cached = compose\(", r"return compose\(rows, width, height, x, y, scale, background\)",
            r"p\.dripText\(this\.entry\.name",
        ],
        "client/render/WatermarkRenderer.java": [
            r"generation == CanvasFonts\.generation\(\) && density == pixels", r"cached = null",
            r"DripLiteStyle\.text\(p, TITLE", r"DripLiteStyle\.halo\(p", r"emblem\(p, 1\.0F, 1\.0F\)",
        ],
        "client/render/DripLiteStyle.java": [
            r"System\.nanoTime\(\) % 4000000000L", r"Math\.cos\(phase - row \* 0\.42\)",
            r"FONT = 12\.5F", r"ROW = 15\.5F",
        ],
        "module/impl/NotificationsModule.java": [
            r'this\.defaultOn\("show_combat", true\)', r'this\.defaultOn\("show_utility", true\)',
            r'this\.defaultOn\("hide_hold_notifs", true\)', r'this\.defaultOn\("hide_bind_only", true\)',
            r'this\.defaultOn\("hide_ingame", false\)',
            r"setting\.parseAndSet\(Boolean\.toString\(value\)\)",
            r"setting\.saveCurrent\(\)",
        ],
        "module/impl/ModOverlayModule.java": [
            r'new BooleanSetting\("screenshot_lowercase", "Drip Lite lowercase", false\)',
            r'new ChoiceSetting\("hud_style", "Style", 1, "Raven", "Drip Lite"\)',
            r"this\.screenshotAnimator\.frame\(this\.dripLiteEntries\(\)",
            r'if \(this\.flag\("screenshot_lowercase"\)\)',
            r"entries\.equals\(this\.cachedEntries\)", r"CanvasFonts\.generation\(\) == this\.cachedFontGeneration",
        ],
        "client/render/BedPlatesRenderer.java": [
            r"CanvasFonts\.guiFace\(1\.0F\)", r"DISTANCE_SIZE = 16\.0F", r"p\.canvasText\(plate\.label.*DISTANCE_SIZE",
            r"scale \*= projectionZoom\(frame\.projection, c\.minecraft\(\)\.gameSettings\.fovSetting\)",
            r"Math\.abs\(projection\[5\]\) \* Math\.tan\(Math\.toRadians\(configuredFov\) \* 0\.5\)",
            r"if \(backgroundOpacity > 0\.0F\)", r"0\.23F \* backgroundOpacity",
            r"scale, range, 0\.93F\)",
        ],
        "client/render/BedPlatesWorld.java": [
            r"CELLS_PER_TICK = 8192", r"SECTION_VISITS_PER_TICK = 32",
            r"this\.tick - bed\.updated >= 20", r"this\.defenses\(bed\);\s*break;",
        ],
        "module/impl/PlayerEspModule.java": [
            r'new NumberSetting\("glow_smoothness", "Glow smoothness", 65\.0, 0\.0, 100\.0',
            r'new NumberSetting\("glow_players", "Glow player limit", 12\.0',
            r"frustum\.isBoundingBoxInFrustum", r"PlayerEspGlowRenderer\.boxFallback\(frame, this\.frameTargets",
            r"\(flags & 4\) != 0", r"base = colored \? team",
        ],
        "client/render/PlayerEspGlowRenderer.java": [
            r"1280\.0F / viewportWidth, 720\.0F / viewportHeight",
            r"640\.0F / w, 360\.0F / h",
            r'glGetUniformLocation\(this\.outlineProgram, "mask"\), OpenGlHelper\.lightmapTexUnit - 33984',
            r"this\.attach\(this\.blurTexture, this\.blurFramebuffer, this\.blurWidth, this\.blurHeight\)",
            r"GL11\.glViewport\(0, 0, this\.blurWidth, this\.blurHeight\)",
            r"glUniform2f\(this\.outlineBlurTexelUniform, 1\.0F / this\.blurWidth, 1\.0F / this\.blurHeight\)",
            r"glDeleteProgram\(this\.blurProgram\)",
        ],
        "client/render/HudPositionEditor.java": [
            r"this\.drawScreenshotControls\(mx, my\);\s*return;", r"CanvasFonts\.GuiFaceScope face = DripLiteStyle\.face\(\)",
            r"this\.screenshotPreview = this\.hud\.screenshotPreview", r"DripLiteStyle\.button\(plan, button, 85, mx, my\)",
        ],
        "client/render/RavenHudEditor.java": [
            r"if \(program != 0\) GL20\.glUseProgram\(0\)",
            r"finally\s*\{\s*if \(program != 0\) GL20\.glUseProgram\(program\)",
        ],
        "client/render/DrawPlan.java": [r"DrawPlan append\(DrawPlan plan\)", r"this\.operations\.addAll\(plan\.operations\)", r'DrawPlan\.Op\("dripText"'],
        "module/impl/GuiModule.java": [
            r"event\.type == 0 && ownedScreen\(c\.minecraft\(\)\.currentScreen\) && Mouse\.isGrabbed\(\)",
            r"void finishEditorClose\(\)", r"inGameHasFocus && !Mouse\.isGrabbed\(\)",
            r"mouseHelper\.grabMouseCursor\(\)",
        ],
        "client/render/NotificationRenderer.java": [r"CanvasFonts\.guiFace\(1\.0F\)"],
        "module/Module.java": [
            r"if \(setting\.isConfigured\(\)\) setting\.saveCurrent\(\);\s*this\.settings\.add\(setting\)",
            r"boolean canToggleFromGui\(\)",
        ],
        "config/ConfigStore.java": [
            r"profileRecords\(manager, read\(this\.path\)\)", r"hasRecords\(manager, root, RecoveredConfiguration\.Scope\.PROFILE\)",
            r'object\(root, "modules"\)', r'object\(container, module\.getId\(\)\)', r'object\(record, "settings"\)',
            r'normalized\.add\("__on", record\.get\("enabled"\)\)', r"if \(parent != null\) ensure\(parent\)",
        ],
        "config/RecoveredConfiguration.java": [
            r"manager\.requestedEnabled\(module\)", r"settings\.add\(restoreSetting\(setting\)\)",
            r"restoreAll\(settings, failure\);\s*restoreAll\(bindings, failure\);\s*restoreAll\(states, failure\)",
            r"if \(!configured\) setting\.markUnconfigured\(\)", r"failure\.addSuppressed\(rollbackFailure\)",
            r"apply\(manager, new JsonObject\(\), RecoveredConfiguration\.Scope\.PROFILE\)",
        ],
        "client/render/ProfileServices.java": [r"Could not reset profile:", r"configurationSession\(\)\.profileApplied\(name\)"],
        "client/render/RecoveredGuiScreen.java": [
            r"entry\.canToggleFromGui\(\)", r"bindingRow = inside\.next\(20\.0F\)",
            r"actions = inside\.next\(20\.0F\)", r"this\.profileError = error\.getMessage\(\)", r"\[Active\]",
        ],
        "module/impl/BedPlatesModule.java": [
            r'new NumberSetting\("background_opacity", "Background opacity", 93\.0, 0\.0, 100\.0, 1\.0\)',
            r"this\.backgroundOpacity\.floatValue\(\) / 100\.0F",
        ],
        "module/impl/BlockInModule.java": [
            r"boolean canToggleFromGui\(\)\s*\{[^}]*return true;", r"if \(!ItemActionHelper\.ready\(c\)\) return false",
            r"long revision = RotationService\.mouseInputRevision\(\)", r"this\.observedMouseInput = revision",
            r"ItemActionHelper\.useBlock\(c, this, hit\)",
        ],
        "client/rotation/RotationService.java": [r"mouseInputRevision\+\+", r"long mouseInputRevision\(\)"],
        "config/SettingCodec.java": [
            r'"hud_style"\.equals\(choice\.getKey\(\)\) && "Screenshot"\.equals\(label\)',
            r'choice\.getOptions\(\)\.contains\("Drip Lite"\)\) label = "Drip Lite"',
            r"choice\.getOptions\(\)\.get\(index\)\.equals\(label\)",
            r"Double\.isFinite\(number\) && number == index && index >= 0 && index < choice\.getOptions\(\)\.size\(\)",
        ],
    }
    errors = []
    sources = {}
    for relative, patterns in contracts.items():
        text = (src / "dev/slinky" / relative).read_text(encoding="utf-8-sig")
        sources[relative] = text
        for pattern in patterns:
            if re.search(pattern, text) is None:
                errors.append([relative, "missing HUD source contract", pattern])
    access = sources["client/render/RenderAccess.java"]
    if "glLoadIdentity" in code_only(access) or access.count("GL11.glLoadMatrix(baseModel)") != 2:
        errors.append(["RenderAccess.java", "canvas shapes/gradient must preserve the caller's base model matrix"])
    glow = sources["module/impl/PlayerEspModule.java"]
    if "glowTargets(c, e.timerRenderPartialTicks)" in glow:
        errors.append(["PlayerEspModule.java", "fallback must reuse event-6 targets, not recull under screen projection"])
    shader = sources["client/render/PlayerEspGlowRenderer.java"]
    gaussian = re.search(r"String GAUSSIAN\s*=\s*(.*?);\s*\n", shader, re.DOTALL)
    if gaussian is None:
        errors.append(["PlayerEspGlowRenderer.java", "missing separable Gaussian source"])
    else:
        literals = re.findall(r'"(?:[^"\\]|\\.)*"', gaussian.group(1))
        body = "".join(json.loads(literal) for literal in literals)
        if "for(int i=-6;i<=6;i++)" not in body or "return sum/total" not in body or "uv+step*d" not in body:
            errors.append(["PlayerEspGlowRenderer.java", "Gaussian must densely sample neighboring texels and normalize weights"])
        if "(1.0F + 2.0F * smoothness) / this.blurWidth" in shader or "blurTexel.y*(" in shader:
            errors.append(["PlayerEspGlowRenderer.java", "smoothness must not stretch sample spacing"])
        for smoothness in (0, 0.5, 1):
            sigma = 0.9 + 1.6 * smoothness
            weights = [math.exp(-0.5 * i * i / (sigma * sigma)) for i in range(-6, 7)]
            normalized = [w / sum(weights) for w in weights]
            if abs(sum(normalized) - 1) > 1e-12 or normalized != list(reversed(normalized)) or min(normalized) <= 0:
                errors.append(["PlayerEspGlowRenderer.java", "dense Gaussian arithmetic must preserve DC and symmetric positive support"])
    if '"animate".equals(key)' in sources["module/impl/ModOverlayModule.java"]:
        errors.append(["ModOverlayModule.java", "Animate must remain visible for Screenshot style"])
    outline = re.search(r"String OUTLINE\s*=\s*(.*?);\s*\n", shader, re.DOTALL)
    if outline is not None and gaussian is not None:
        parts = re.findall(r'"(?:[^"\\]|\\.)*"|\bGAUSSIAN\b', outline.group(1))
        outline_body = "".join(body if part == "GAUSSIAN" else json.loads(part) for part in parts)
        depth = 0
        for token in re.findall(r"\{|\}|\buniform\b", outline_body):
            if token == "{": depth += 1
            elif token == "}": depth -= 1
            elif depth != 0:
                errors.append(["PlayerEspGlowRenderer.java", "GLSL uniforms must be at global scope"])
            if depth < 0:
                break
        if depth != 0 or "edge=max" in outline_body or "vec4(tint*a,a)" not in outline_body:
            errors.append(["PlayerEspGlowRenderer.java", "soft outline must be balanced and omit hard max-dilated/white edges"])
        coverage = [float(value) for value in re.findall(r"texture2D\(mask,[^;]*?\)\.a\*([0-9.]+)", outline_body)]
        if len(coverage) != 5 or abs(sum(coverage) - 1.0) > 0.000001:
            errors.append(["PlayerEspGlowRenderer.java", "mask coverage filter must have normalized center/neighbor weights"])
    if "GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA" not in shader:
        errors.append(["PlayerEspGlowRenderer.java", "premultiplied output requires premultiplied compositing"])
    listing = sources["client/render/ScreenshotModuleList.java"]
    if ".clip(" in code_only(listing):
        errors.append(["ScreenshotModuleList.java", "Drip Lite must not mix raw scissor coordinates with editor GUI geometry"])
    if 'op.kind.equals("dripText")' not in access or 'DripLiteStyle.accent((int)v[0])' not in access:
        errors.append(["RenderAccess.java", "cached Drip Lite text must evaluate color on every draw"])
    duration = re.search(r"DURATION = ([0-9.]+)", listing)
    if duration is not None:
        duration = float(duration.group(1))
        for dt in (1 / 240, 1 / 60, 1 / 30, 0.05):
            progress = 0.0
            for target in (1.0, 0.0, 1.0, 0.0):
                for _ in range(int(duration / dt) + 2):
                    previous = progress
                    progress = min(target, progress + dt / duration) if target > progress else max(target, progress - dt / duration)
                    visibility = progress * progress * (3 - 2 * progress)
                    if not 0 <= visibility <= 1 or (target - previous) * (progress - previous) < 0:
                        errors.append(["ScreenshotModuleList.java", "animation arithmetic overshoots or reverses direction"])
                if progress != target:
                    errors.append(["ScreenshotModuleList.java", "animation does not settle at the target"])
    editor = sources["client/render/RavenHudEditor.java"]
    if not re.search(r"this\.mc\.displayGuiScreen\(null\);\s*if \(gui != null\) gui\.finishEditorClose\(\)", editor):
        errors.append(["RavenHudEditor.java", "focus cleanup must happen after the old screen is closed"])
    for row in range(20):
        samples = [0.5 - 0.5 * math.cos(t / 4.0 * math.tau - row * 0.42) for t in (0, 1, 2, 3, 4)]
        if min(samples) < 0 or max(samples) > 1 or abs(samples[0] - samples[-1]) > 1e-12:
            errors.append(["DripLiteStyle.java", "color cycle must remain bounded and periodic"])
    for fov in (30, 70, 110):
        baseline = 1.0 / math.tan(math.radians(fov) * 0.5)
        for multiplier in (1, 2, 4, 8):
            projection_y = baseline * multiplier
            zoom = projection_y * math.tan(math.radians(fov) * 0.5)
            if abs(zoom - multiplier) > 1e-10:
                errors.append(["BedPlatesRenderer.java", "projection zoom arithmetic does not track the camera magnification"])
    return errors


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
    files, errors = check(root)
    print(json.dumps({
        "files_checked": len(files),
        "checks": ["Java delimiters/literals", "package paths", "local import targets", "conflict markers", "module setting keys", "action tick/queue guards", "GUI catalog schema", "HUD crash/density/cache source contracts", "Glow sampler/budget contracts and Gaussian weights", "GLSL text scopes and animation arithmetic", "Drip Lite label migration, color-cycle and projection-zoom arithmetic", "editor focus lifecycle source guards", "BlockIn restoration source guards"],
        "errors": errors,
        "note": "Source-only checks, not a Java parser, compilation, unit test or game execution.",
    }, ensure_ascii=False, indent=2))
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
