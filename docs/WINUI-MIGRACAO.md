# Migração CodePad → WinUI 3

## Por que o JavaFX não iguala o Explorador

O Explorador do Windows 11 é **WinUI nativo**. O material Mica é desenhado pelo **DWM** na superfície da janela **antes** da UI; os controlos WinUI compõem-se em cima com fundos transparentes.

No **JavaFX**:

- O motor **Prism/Glass** desenha um buffer próprio (muitas vezes opaco ou branco).
- `DwmSetWindowAttribute` até pode ser aceite, mas o conteúdo JavaFX **tapona** o Mica.
- Hacks (`setOpaque(false)`, JNA, CSS transparente) são frágeis e variam por versão do JDK/JavaFX e pelo instalador jpackage.

Por isso **não mudámos antes**: o projeto inteiro está em **Java** (~2800 linhas no `EditorController`, RichTextFX, Maven, jpackage). WinUI implica **outra linguagem (C#)**, outro build e reimplementar funcionalidades — não é uma opção de menu, é um **novo cliente**.

## O que o WinUI oferece

```xml
<Window.SystemBackdrop>
    <MicaBackdrop Kind="BaseAlt" />
</Window.SystemBackdrop>
```

Isto é o mesmo tipo de efeito que o Explorador (Mica Alt / tabbed). Sem lutar contra o Glass do JavaFX.

## Estado atual (fase: Mica + notas)

| Componente | Java (atual) | WinUI (`winui/CodePad.WinUI`) |
|------------|--------------|-------------------------------|
| Mica / wallpaper | Instável | `<MicaBackdrop Kind="BaseAlt" />` |
| Notas / abas | RichTextFX + TabPane | `TabView` + `TextBox` transparente |
| Rascunhos | `drafts.dat` DRAFTS_V2 | Mesmo ficheiro em `%LOCALAPPDATA%\CodePad` |
| Abrir / Salvar | Sim | Sim (.txt, .md) |
| Calendário / código | Sim | Próxima fase |
| Instalador | jpackage | `package-winui.ps1` |

## Como compilar o protótipo WinUI

Requisitos: Windows 11, Visual Studio 2022 **ou** Build Tools com workload **Windows application development**, .NET 8 SDK.

```powershell
cd j:\ProjetosJava\CodePad\winui
.\package-winui.ps1
# ou, após build:
cd CodePad.WinUI
dotnet run -c Release -p:Platform=x64
```

Abra **Exibir → Vidro/Mica** não é necessário: o Mica vem sempre ativo nesta versão WinUI.

Se `dotnet build` falhar:

- **ExpandPriContent / AppxPackage** — instale **Visual Studio 2022** (ou Build Tools) com a carga **Desenvolvimento de aplicativos da plataforma Windows universal** e **Windows App SDK C# Templates**.
- Compile preferencialmente no **Developer PowerShell for VS**, não só com `dotnet` CLI isolado.

O protótipo usa Mica nativo:

```xml
<Window.SystemBackdrop>
    <MicaBackdrop />
</Window.SystemBackdrop>
```

## Plano de migração sugerido (fases)

1. **Protótipo** — janela Mica + layout notas/planejamento (feito em `winui/`).
2. **Dados** — portar `EventStore`, rascunhos, `app.properties` para C# (mesmos caminhos em `%LOCALAPPDATA%\CodePad`).
3. **Editor** — syntax highlighting (WinUI `RichEditTextBox`, ou WebView2 + Monaco, ou biblioteca terceiros).
4. **Funcionalidades** — menus, encoding, abas, busca (paridade com `EditorController`).
5. **Distribuição** — instalador WinUI; manter release Java até paridade ou renomear CodePad WinUI como app principal.

## Manter Java ou WinUI?

- **Continuar só em Java**: Mica “estilo Explorador” continuará **limitado**; melhor aceitar tema escuro sólido.
- **WinUI como app principal**: visual nativo correto; investimento de reescrita.

Recomendação: validar o protótipo WinUI no seu PC; se o Mica estiver correto, seguir migração por fases e congelar novas features grandes no Java.
