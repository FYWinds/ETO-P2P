# Notice

Parts of this interface kit (`fyi.fyw.etolib`) are ported from **GTNH Planner**, Copyright (c) 2026 Sbancuz, under the
MIT License (reproduced below): its ETO fork (https://github.com/FYWinds/gtnh-planner-mod, branch `eto`) of
https://github.com/jackwrichards/gtnh-planner-mod. The ports are Kotlin translations of the planner's Java, with
planner-specific parts left out.

| Kit file | Ported from (path under the planner's main Java package) |
| --- | --- |
| `ui/Look.kt` | `ui/theme/Hyb.java` (and the planner's chip and pill drawing in `ui/popup/FeedbackPanel.java`) |
| `ui/Fmt.kt` | `ui/theme/Fmt.java` |
| `ui/ScrollBar.kt` | `ui/theme/ScrollBar.java` |
| `ui/Icons.kt` | `ui/theme/Icons.java` |
| `ui/IconShadows.kt` | `ui/theme/IconShadows.java` |
| `ui/Popup.kt` | `ui/popup/Popup.java` |
| `ui/PickList.kt` | `ui/popup/PickList.java` |
| `ui/TextPopup.kt` | `ui/popup/TextPopup.java` |
| `ui/TextBox.kt` | the select-all fields of `ui/popup/TextPopup.java` and `ui/popup/NumberPopup.java` |
| `ui/Tip.kt` | `ui/popup/Tip.java`, and the tip drawing of `ui/BoardScreen.java` |
| `ui/TextInput.kt` | `ui/TextInput.java` |
| `ui/TextWrap.kt` | `ui/TextWrap.java` |
| `ui/UiText.kt` | `ui/UiText.java` |
| `text/TextMatch.kt` | `TextMatch.java` |

The other kit files (`ui/Menu.kt`, `ui/SearchField.kt`, `ui/Chip.kt`, `ui/IconButton.kt`, `ui/Dialogs.kt`,
`ui/Label.kt`, `ui/UiDsl.kt`) are new, drawn in the same look.

## MIT License

Copyright (c) 2026 Sbancuz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
