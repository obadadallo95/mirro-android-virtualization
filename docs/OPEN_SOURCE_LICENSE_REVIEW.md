# Mirro Open-Source and IP Review

**Status:** architecture/reuse screening only. No external source was copied or vendored into Mirro by this audit.

**Important:** this is not legal advice. A release that incorporates code, binaries, generated artifacts, or patented implementation techniques needs a formal license/IP review, a complete dependency/SBOM scan, and confirmation of current repository terms at the exact commit used.

## Executive conclusion

Mirro may study the architecture of public virtualization projects, but it should not copy their framework code at this stage. The most relevant projects combine deep hidden-API hooks, native hooks, framework singleton replacement, process tricks and policy-sensitive identity behavior. Their apparent compatibility is tied to code, version assumptions, commercial licenses, or deployment models that do not transfer cleanly to a Play-distributed app.

The safe current policy is:

- use Android/AOSP documentation and Apache-2.0 Android platform/source components only after tracking their exact license/NOTICE obligations;
- treat VirtualApp, VirtualXposed, BlackBox and DroidPlugin as architecture references, not dependencies;
- do not use any repository with unclear provenance or no clear license grant;
- review transitive native-hook code separately, even when a top-level fork says “Apache-2.0”;
- maintain a source inventory and SPDX license record before any future implementation selects an external library.

## Review criteria

Each candidate is evaluated for:

- repository and exact source location;
- stated license and whether a clear license file exists;
- maintenance state and Android-version relevance;
- commercial-use restrictions and attribution/notice duties;
- copyleft/linking obligations;
- patent/IP or provenance risk;
- whether the architecture can be studied without copying implementation;
- recommendation for Mirro.

“Architecture only” means ideas may inform design and tests, but no source, copied class structure, generated output, names, binary, or derivative implementation should enter Mirro without a separate approval.

## Candidate review

### Android platform and AOSP

| Item | License/status | Reuse assessment | Recommendation |
|---|---|---|---|
| Android Open Source Project source | AOSP components generally carry Apache-2.0 or component-specific notices; exact file headers control | Reuse can be possible for applicable platform code, but Android framework internals are not a drop-in app library | Use public API/documentation and cite source; copy only exact, reviewed files with NOTICE/SBOM tracking |
| Android Developers documentation | Documentation terms and content license apply; not application source | Documentation can guide behavior and test design | Link and attribute where needed; do not treat docs as code license |
| AndroidX / Jetpack APIs already used by Mirro | Generally Apache-2.0; exact artifacts and NOTICE files control | Normal dependency use is expected subject to Gradle/SBOM policy | Keep existing dependency governance; do not add a virtualization framework transitively without review |
| AOSP AVF/Microdroid | Source components vary; platform integration and hardware/privilege assumptions matter | Architecture/reference only for Mirro's current normal-app model | Do not assume AVF is a drop-in full Android guest |

Sources: [AOSP license overview](https://source.android.com/docs/setup/about/licenses), [Android Open Source Project](https://source.android.com/), [AndroidX](https://developer.android.com/jetpack/androidx).

### VirtualApp

| Field | Finding |
|---|---|
| Repository | [asLody/VirtualApp](https://github.com/asLody/VirtualApp), [English README](https://github.com/asLody/VirtualApp/blob/master/README_eng.md) |
| Public state | The repository says public code stopped updating in December 2017; the README promotes a commercial edition and describes current-version claims separately |
| License | The public material does not present a clean permissive reuse grant for Mirro. The README expressly directs commercial/internal/app-market users toward a business license and asserts ownership/IP protections |
| Commercial restriction | Explicit business-license warning; do not assume GitHub visibility is permission to use in a commercial or Play product |
| Architecture value | High as evidence of multi-process roles, virtual package/component handling, native/Java hooks, path redirection and GMS-specific engineering |
| Compatibility value | Claims of current Android/GMS support are vendor claims; public source age makes them insufficient evidence |
| Mirro decision | **Architecture only. Do not vendor, copy, or derive implementation without written license/IP clearance.** |

The repository's own warning is enough to make “copy first, clarify later” unacceptable.

### DroidPlugin

| Field | Finding |
|---|---|
| Repository | [DroidPluginTeam/DroidPlugin](https://github.com/DroidPluginTeam/DroidPlugin) |
| License | LGPL-3.0, with a repository `LICENSE` file |
| Maintenance | Public activity is old relative to current Android releases; GitHub metadata shows limited recent activity |
| Copyleft | A library reuse can trigger LGPL notice, source/relinking and modification obligations depending on linking/form and distribution; statically incorporating or modifying requires legal review |
| Technical limitations | README documents notification/resource limitations, inability to expose normal plugin intent filters externally, and lack of native-layer hooks affecting many native apps |
| Mirro decision | **Architecture only. No vendor.** If ever evaluated as a dependency, perform LGPL linking/source/notice review and a current-Android security review first |

Sources: [repository](https://github.com/DroidPluginTeam/DroidPlugin), [LGPL license](https://github.com/DroidPluginTeam/DroidPlugin/blob/master/LICENSE), [GitHub activity](https://github.com/DroidPluginTeam).

### VirtualXposed

| Field | Finding |
|---|---|
| Repository | [android-hacker/VirtualXposed](https://github.com/android-hacker/virtualxposed) |
| License | GPL-3.0 according to repository metadata and `LICENSE.txt` |
| Maintenance | Latest visible project activity is not a current Android-platform maintenance guarantee; README limits support to Android 5–10 and says resource hooks are unsupported |
| Commercial restriction | README says commercial use is not allowed and refers to VirtualApp's declaration |
| Technical composition | VirtualApp plus Epic/Exposed-style hook layers, native/I/O redirection and UI integration; the wiki states that native hooks are used for selected behavior |
| Copyleft | GPL obligations are incompatible with a proprietary app integration unless Mirro is prepared to comply with the license for the complete derivative/distributed work; exact dependency graph needs review |
| Mirro decision | **Architecture only. Do not use as a dependency or copy code.** |

Sources: [repository](https://github.com/android-hacker/virtualxposed), [architecture wiki](https://github.com/android-hacker/VirtualXposed/wiki/How-does-VirtualXposed-work).

### BlackBox original repository

| Field | Finding |
|---|---|
| Repository | [FBlackBox/BlackBox](https://github.com/FBlackBox/BlackBox) |
| Public state | Repository README states the project was dissolved; GitHub page shows 297 commits and no clear license file in the visible repository listing |
| License | **Unclear from the public repository state reviewed.** Do not infer a license from forks or from the absence of a license file |
| Technical value | Demonstrates a virtual engine and contains public issues about loader/component failures; useful for failure taxonomy |
| Mirro decision | **Architecture/issues only. No copying, dependency use, or binary reuse.** |

### BlackBox forks

| Item | Stated license/status | Risk | Mirro decision |
|---|---|---|---|
| [TeguFy/blackbox-android](https://github.com/TeguFy/blackbox-android) | README states Apache-2.0; fork credits VirtualApp, VirtualAPK, Dobby and xDL | Top-level Apache label does not clear upstream provenance, transitive dependencies, patents or copied code; claims Android 5–14+ and native hooks are not independent proof | Architecture only until complete provenance/SBOM review |
| [ALEX5402/NewBlackbox](https://github.com/ALEX5402/NewBlackbox) | Public fork with feature/UID/GMS/background claims; exact dependencies and upstream lineage require review | Marketing/self-claims; potential copied VirtualApp/BlackBox code and native-hook licensing | Architecture/claims only |
| [BotBotHack/NewblackboxSRC](https://github.com/BotBotHack/NewblackboxSRC) | Public fork/sample claims | Same provenance and license concerns | Architecture only |

The phrase “Apache-2.0” in a fork's README is not enough. Every file and native dependency needs provenance, license, NOTICE, copyright and patent analysis.

### Dobby

| Field | Finding |
|---|---|
| Repository | [jmpews/Dobby](https://github.com/jmpews/Dobby) |
| License | Repository page identifies Apache-2.0 |
| Technical role | Inline/native hook framework; relevant to how competitors observe native loader behavior |
| Provenance | README credits frida-gum, minhook, substrate, V8, Dart and VIXL; these credits require transitive provenance review for the selected files/build |
| Product risk | Inline hooking patches executable code, can crash or alter security behavior, and is not a substitute for a public-API Play-safe contract |
| Mirro decision | **Do not add for the first milestone.** Any future use requires exact-version license/SBOM, security, non-SDK, policy and anti-tamper review |

### xDL

| Field | Finding |
|---|---|
| Repository | [hexhacking/xDL](https://github.com/hexhacking/xDL) |
| License | MIT according to the repository |
| Technical role | Enhanced Android dynamic-loader functions; public README explicitly discusses bypassing linker namespace restrictions |
| Product risk | A permissive license does not make bypassing platform linker restrictions acceptable for Mirro's product or Play distribution. It can also change the security and crash surface materially |
| Mirro decision | **Architecture/reference only for now.** Do not use to bypass linker namespaces or force native loading in the first milestone |

### VirtualAPK and related frameworks

VirtualAPK is relevant as historical plugin/component architecture, but it was not selected as a dependency in this audit. Any future review must record its exact repository, commit, license, dependency tree and maintenance state; do not assume a similarly named fork has the upstream license.

### Academic work

| Work | Rights/status | Use |
|---|---|---|
| Boxify / USENIX Security 2015 proceedings | Research publication; copyright and publication terms apply, not a code license | Cite and study the reference-monitor/process/UID architecture; do not copy text/code beyond permitted use |
| Anception | Research paper on Android app virtualization; paper rights apply | Architecture and threat-model reference only |

Sources: [USENIX proceedings](https://www.usenix.org/sites/default/files/sec15_full_proceedings.pdf), [Anception](https://arxiv.org/abs/1401.6726).

## Dependency-specific legal and security checklist

Before adding any virtualization or native-hook dependency:

1. Pin an immutable commit/tag and archive the source used.
2. Confirm the license file at that commit, not only the repository badge.
3. Inventory every source, generated file, prebuilt AAR/SO, vendored submodule and build script.
4. Produce an SPDX/SBOM record and NOTICE/attribution output.
5. Trace transitive dependencies and all upstream credits.
6. Determine whether the dependency is GPL/LGPL/Apache/MIT or has a custom commercial restriction.
7. Review patents, trademarks, export controls and any business-license notice with counsel.
8. Confirm whether code performs inline hooks, linker-namespace bypasses, hidden-API access, anti-tamper bypass, or package/UID spoofing.
9. Run Android API/OEM compatibility tests and Play pre-launch checks.
10. Document how the dependency is disabled or removed if a future Android release blocks it.

## Copying versus independent implementation

The following practices reduce contamination risk while researching competitor code:

- read public README/issues/docs to understand concepts and failure modes;
- write Mirro's own requirements, invariants and tests before implementation;
- avoid copying class names, method structures, comments, control-flow patterns or source snippets;
- do not paste competitor code into an AI prompt that will generate Mirro implementation;
- use Android/AOSP public APIs and independent tests as the primary specification;
- preserve links and research notes in architecture docs, not copied source;
- ask for legal review when a design is materially similar to a protected implementation or a commercial warning exists.

## Recommended current license posture for Mirro

1. Keep this research phase documentation-only.
2. Implement the first runtime-observability milestone from Mirro's own contracts and fixtures.
3. Prefer platform/public APIs and existing already-approved AndroidX dependencies.
4. If native diagnostics are later needed, evaluate a small, independently implemented component before importing a hook framework.
5. Maintain a `THIRD_PARTY_NOTICES`/SBOM process before adding any external virtualization code.
6. Treat all competitor support claims as hypotheses requiring reproducible device evidence.

## Reference links

- [VirtualApp](https://github.com/asLody/VirtualApp)
- [DroidPlugin](https://github.com/DroidPluginTeam/DroidPlugin)
- [VirtualXposed](https://github.com/android-hacker/virtualxposed)
- [BlackBox original](https://github.com/FBlackBox/BlackBox)
- [BlackBox fork example](https://github.com/TeguFy/blackbox-android)
- [Dobby](https://github.com/jmpews/Dobby)
- [xDL](https://github.com/hexhacking/xDL)
- [GitHub licensing guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
- [Android Open Source Project licenses](https://source.android.com/docs/setup/about/licenses)
