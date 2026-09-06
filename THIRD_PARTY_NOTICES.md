# Third-party notices / 第三方许可证声明

AzureQL includes or derives work from the following open-source projects. Their licenses apply to
the corresponding components and do not replace the license of AzureQL itself.

AzureQL 包含或派生自以下开源项目。对应第三方许可证只适用于相应组件，不会替代 AzureQL 自身许可证。

## Commercial use / 商业使用

These licenses are **not non-commercial-only licenses**. Both LGPL-2.1 and Apache-2.0 permit
commercial use and distribution, provided their respective conditions are met. “Free software” in
the LGPL refers to software freedom rather than a restriction to free-of-charge or non-commercial
use. This notice is a practical compliance summary, not legal advice; distributors remain
responsible for reviewing the complete license texts.

这两种许可证都**不是“仅限非商业使用”许可证**。LGPL-2.1 与 Apache-2.0 均允许商业使用和分发，但分发者
必须满足各自条款。LGPL 中的“自由软件”强调使用、研究、修改与再分发的自由，并不等于只能免费或非商业使用。
本文件是便于执行的合规摘要，不构成法律意见；正式分发者仍应阅读完整许可证原文。

## Sora Editor 0.24.6

- Component: `io.github.rosemoe:editor` and `io.github.rosemoe:language-monarch`
- Copyright: Rosemoe and Sora Editor contributors
- License: GNU Lesser General Public License v2.1 (`LGPL-2.1`)
- Source for the pinned release: <https://github.com/Rosemoe/sora-editor/tree/0.24.6>
- License text: <https://github.com/Rosemoe/sora-editor/blob/0.24.6/LICENSE>

AzureQL links these unmodified Maven components into the Android application. The exact dependency
coordinates and pinned version are declared in `feature/script/build.gradle.kts` and
`gradle/libs.versions.toml`, so recipients can obtain, inspect, replace, and rebuild the library.

For binary distribution, keep this attribution and the LGPL license available, do not prohibit
reverse engineering performed to debug a modified LGPL component, and preserve a practical way for
recipients to modify or replace that component. AzureQL publishes the complete application source
at each GitHub release tag together with the exact Maven coordinates, which provides the rebuild and
replacement path used by this project. If a distributor modifies Sora Editor itself, those library
modifications must remain available under the LGPL terms.

二进制分发时应保留本归属声明与 LGPL 许可证入口，不得禁止为了调试 LGPL 组件修改版而进行的逆向工程，并应为
接收者保留修改或替换该组件的实际途径。AzureQL 在每个 GitHub Release 标签提供完整应用源码与准确 Maven 坐标，
以此提供重新构建和替换途径。如果分发者修改了 Sora Editor 本身，相应库修改还必须按 LGPL 条款提供。

## monarch-kt language definitions

- Component: selected Python, JavaScript, TypeScript, Shell and YAML Monarch language definitions
- Copyright: dingyi222666 and monarch-kt contributors
- License: Apache License 2.0 (`Apache-2.0`)
- Source: <https://github.com/dingyi222666/monarch-kt>
- License text: <https://github.com/dingyi222666/monarch-kt/blob/main/LICENSE>

The derived Kotlin language-definition files retain an Apache-2.0 attribution header in source.

Commercial redistribution is permitted. Distributors must retain the applicable copyright,
license, and attribution notices; modified derived files must carry a prominent modification notice.

Apache-2.0 允许商业再分发。分发者须保留适用的版权、许可证和归属声明；修改派生文件时应显著标明修改。

## Release checklist / 发布检查

- Keep this file in the source archive and link it from the README and release notes.
- Publish the source tree for the exact released tag, including the pinned dependency coordinates.
- Keep the upstream license links accessible and retain the attribution headers in derived grammar files.
- Do not claim Sora Editor or the Monarch definitions are authored by AzureQL.
- Re-check the upstream license before changing versions or distributing a modified library build.

- 在源码归档中保留本文件，并从 README 与发布说明链接到这里。
- 发布与 APK 完全对应的标签源码，包含固定的依赖版本与坐标。
- 保持上游许可证入口可访问，并保留派生语法文件中的归属头。
- 不得把 Sora Editor 或 Monarch 语法定义宣称为 AzureQL 原创。
- 升级版本或分发修改后的库之前，重新核对上游许可证。
