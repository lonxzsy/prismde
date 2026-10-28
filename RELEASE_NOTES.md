### 🚀 What's New in v1.0.4

#### 🎨 Material 3 Expressive Transformation
- Full upgrade to **Material 3 Expressive** design system (`MaterialExpressiveTheme`, `MotionScheme.expressive()`).
- New `SplitButtonLayout` for the primary Build action with build preset configuration.
- Expressive wavy progress indicators (`LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`) across build sheets, preview viewports, and AI assistant.
- Spring physics animations (`bouncyClickable` with `Spring.DampingRatioMediumBouncy`) across list items, cards, and buttons.
- Floating island navigation and clustered expressive settings cards.

#### 👁️ Universal In-App ImGui Preview GUI
- **Live OpenGL ES 3.0 Viewport**: Real-time interactive Dear ImGui preview directly inside PrismDE without needing to deploy or run a target game.
- **Incremental Fast Compilation**: Compiles user GUI files and ImGui core incrementally (`Compile++ : <filename>`) with real-time status updates in ~1-2 seconds.
- **Universal Project Compatibility**: Deep recursive discovery of ImGui source files (`imgui.cpp`, `imgui_draw.cpp`, `imgui_widgets.cpp`, `imgui_tables.cpp`, `imgui_demo.cpp`) and backends across complex arbitrary directory structures (e.g. `hygen`, `Wolsaw`, `hygen-pubgm`).
- **Universal Header Resolution**: Multiline `LOCAL_C_INCLUDES` expansion and ancestor folder inclusion with standard C library header shadowing protection (`string.h`).
- **Flexible Entry-Point Discovery**: Multi-signature auto-detection supporting `ui::menu::render()`, `UI::Menu::Draw()`, `Menu::Render(&open)`, `DrawMenu()`, `DrawUI()`, and others.
- **Custom Fonts & Icons**: Native 1:1 scale rendering supporting embedded fonts (`Fonts::LoadFonts`, `UI::FontLoader`, `AntiloseLoadFonts`).
- **Touch Gesture Protection**: Frame-latched touch input with `requestDisallowInterceptTouchEvent` preventing bottom sheet gesture conflicts.
- **Project Cache Isolation**: Independent object file caches (`objs_${projectKey}`) eliminating cross-project symbol contamination.

#### 🤖 AI Assistant & Autonomous Agent Upgrades
- **WebFetch Tool (`web_fetch`)**: Fetch web documentation, guides, and GitHub repositories directly into context with SSRF protection and clean Markdown conversion.
- **Binary Asset Downloader (`download_file`)**: Download fonts and assets with automatic TTF-to-C++ byte array header generation.
- **Deep Thinking UI (`ThinkingCard`)**: M3 Expressive collapsible reasoning card with live elapsed timer, first-line preview, spring animation, and 1-tap copy button.
- **Chat Rendering & Animation Fixes**: Completely eliminated chat flickering and text disappearing during streaming.
- **Pure Chat Provider Integrations**: Native direct chat endpoints for Google Antigravity and Google AI Studio (Gemini API) decoupled from compiler diagnostic explanation templates.
- **Universal Tool Parser**: Robust XML parameter extraction supporting any tool arguments and self-closing tags.

#### ⚙️ Build System, NDK & Gradle Fixes
- **CRT & Exception Unwinding**: Provisioned real `libgcc.a` (`-Wl,--exclude-libs,libgcc.a -lgcc`) resolving `undefined reference to '_Unwind_Resume'`.
- **Bionic Tagged Pointers Fix**: OpenJDK 17.0.21 package discovery with ARM64 Tagged Pointers patch for Android 12+/15.
- **Smart AGP Adapter**: Automatically adapts legacy AGP (3.x/4.x) in `build.gradle` to AGP 7.4.2/8.2.2 for seamless compatibility with Gradle 7+/8+ and OpenJDK 17.
- **Smart AAPT2 Resource Normalizer**: Automatically normalizes raw hex colors in `android:windowBackground` into `@color/window_background` to prevent AAPT2 linking errors.
- **Executable Artifact Discovery**: Seamless detection of `BUILD_EXECUTABLE` outputs alongside shared libraries (`BUILD_SHARED_LIBRARY`).
- **Build-Tools Aliasing**: Backward-compatible aliasing (`33.0.1`, `30.0.3` -> `34.0.0`) without hardcoded `package.xml`.
- **Platform Archive Downloads**: Added SDK platform revisions `r04`-`r06` resolving HTTP 404 on Android SDK Platform 28.

#### 🐛 Direct Telegram Bug Reporting
- 1-tap automated bug reporting in `BuildBottomSheet` and `ImGuiPreviewBottomSheet`.
- Transmits complete device hardware specs, Android OS version, SDK API, CPU architecture, RAM, storage, and compiler failure logs directly to Telegram Bot.
