#!/usr/bin/env python3
"""
Camel-Kit CLI: A toolkit for designing Apache Camel integrations with AI coding assistants.

This module provides the main CLI entry point and agent configuration.
"""

import time
from pathlib import Path
from typing import Optional

import typer
from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.text import Text

from . import catalog as catalog_module

__version__ = "0.1.0"

# Console for rich output
console = Console()

# Banner ASCII art (block letters)
BANNER = """
 ██████╗ █████╗ ███╗   ███╗███████╗██╗          ██╗  ██╗██╗████████╗
██╔════╝██╔══██╗████╗ ████║██╔════╝██║          ██║ ██╔╝██║╚══██╔══╝
██║     ███████║██╔████╔██║█████╗  ██║  █████╗  █████╔╝ ██║   ██║
██║     ██╔══██║██║╚██╔╝██║██╔══╝  ██║  ╚════╝  ██╔═██╗ ██║   ██║
╚██████╗██║  ██║██║ ╚═╝ ██║███████╗███████╗     ██║  ██╗██║   ██║
 ╚═════╝╚═╝  ╚═╝╚═╝     ╚═╝╚══════╝╚══════╝     ╚═╝  ╚═╝╚═╝   ╚═╝
"""

# Kaoto camel ASCII art
CAMEL_ART = r"""
                                                                                                                  `,"^`'.
                                                                                                                .^:::::::::,,"^^``''
                                                                                                               .,::::::,:;;Il!ii>>><<<>i!l"
                                                                                                              ',:I>?1[>ii>>><<~~+++__-?{tuf<.
                                                           ."`                  '"`                           ,<~-]{|f)+~~++___--???]1jnuuvn[`
                                                          'I>i,                ':II,'                         ,I>[{[{\j/]--??]][[[}(frnnnnuuu/I.
                                                         `l<<~~l'             `IllllI^                        :!!!~)f\|rr1[}}}}}(UbaaabwOQQQJvt>.
                                                        ^i<~+__-~,           ^liiiiiii;'                      ;iiii>+)rxf/({}}jma*****ahb0n{<I^.
                                                       ,>~+__-?]]?l.        ,><<<<<<<<<i,                     I><<<<<<_)rux)ndoakpQn}!,`.
                                                     .;<+__-??][}}}+^     .:<~~~~~~~~~~~<!`                   l~~~~~~~~~-1tuXf<,^.
                                                    'l~+_-??][[}{1))}l.  .I~______________~;.                .!++++++++++_-}|}`
                                                   'i+_--?][[}{11)(||(+`.!_----------------_>^               .i--------------_:
                                                  ^<__-?][[}{11)(||\/t/}+?]]]]]]]]]]]]]]]]]]]-l.             .i??????????????-l
                                                 :~_-?][[}{11)((|\//\}<II~][[[[[[[[[[[[[[[[[[[[+,            .<][[[[[[[[[[[[[]<`
                                               .;+-?]][}{{1)((|\\|[<I::;Il>]{{{{{{{{{{{{{{{{{{{{[>'          .<}}}}}}}}}}}}}}}?,
                                              .!_??][}}{1)((|\|}<I::::;l!!i>]111111111111111111111];.        .~{{{{{{{{{{{{{{{}i.
                                             '>-?][}}{1))(||{~I::::;;Il!i><<<_1((((((((((((((((((((1+^       .+)))))))))))))))1-`
                                            "<?][}}{1))(({+l:::::;;IIli>><~~++_1|||||||||||||||||||||{l.     ._((((((((((((((((1:
"""

TAGLINE = "Camel-Kit - Design Apache Camel Integrations with AI"

def show_banner(with_camel: bool = True) -> None:
    """Display the colored camel-kit banner with gradient effect."""
    from rich.align import Align

    # Apache Camel orange/brown gradient (based on Camel logo colors)
    colors = ["#F4AF23", "#EC7826", "#D4691A", "#B86B1B", "#995B35", "#7A4A2A"]

    console.print()

    # Show camel art first if requested (no centering - art is pre-formatted)
    if with_camel:
        # Use strip('\n') to preserve leading spaces in lines (important for ASCII art)
        camel_lines = CAMEL_ART.strip('\n').split("\n")
        for i, line in enumerate(camel_lines):
            color = colors[i % len(colors)]
            console.print(Text(line, style=color), soft_wrap=True, overflow="ignore")

    # Show text banner - normalize widths and center
    banner_lines = BANNER.strip('\n').split("\n")
    max_width = max(len(line) for line in banner_lines)
    for i, line in enumerate(banner_lines):
        color = colors[i % len(colors)]
        padded_line = line.ljust(max_width)
        console.print(Align.center(Text(padded_line, style=color)))

    console.print(Align.center(Text(TAGLINE, style="italic #F4AF23")))
    console.print()

# Agent configuration - single source of truth
# Key should match the actual CLI/tool name
AGENT_CONFIG = {
    "bob": {
        "name": "IBM Project Bob",
        "folder": ".bob/commands",
        "file_format": "md",
        "install_url": None,  # IDE-based, no CLI install
        "requires_cli": False,
        "description": "IBM's AI-powered development assistant",
    },
    # Future agents (commented out for now)
    # "claude": {
    #     "name": "Claude Code",
    #     "folder": ".claude/commands",
    #     "file_format": "md",
    #     "install_url": "https://docs.anthropic.com/en/docs/claude-code",
    #     "requires_cli": True,
    #     "description": "Anthropic's Claude coding assistant",
    # },
    # "copilot": {
    #     "name": "GitHub Copilot",
    #     "folder": ".github/agents",
    #     "file_format": "md",
    #     "install_url": None,
    #     "requires_cli": False,
    #     "description": "GitHub's AI pair programmer",
    # },
}

# CLI app
app = typer.Typer(
    name="camel-kit",
    help="Camel-Kit: Design Apache Camel integrations with AI coding assistants",
    no_args_is_help=True,
)


@app.callback(invoke_without_command=True)
def main_callback(ctx: typer.Context) -> None:
    """Show banner when help is displayed or no command given."""
    if ctx.invoked_subcommand is None:
        show_banner(with_camel=True)


def get_templates_dir() -> Path:
    """Get the templates directory path."""
    return Path(__file__).parent / "templates"


def get_agent_choices() -> list[str]:
    """Get list of available agent names."""
    return list(AGENT_CONFIG.keys())


@app.command()
def init(
    project_name: Optional[str] = typer.Argument(
        None,
        help="Name of the project directory to create",
    ),
    ai: str = typer.Option(
        "bob",
        "--ai",
        "-a",
        help="AI coding assistant to configure",
        show_choices=True,
    ),
    here: bool = typer.Option(
        False,
        "--here",
        help="Initialize in current directory instead of creating new one",
    ),
    force: bool = typer.Option(
        False,
        "--force",
        "-f",
        help="Overwrite existing files without confirmation",
    ),
    camel_version: str = typer.Option(
        "latest",
        "--camel-version",
        "-v",
        help="Apache Camel version to target (use 'latest' for newest LTS)",
    ),
    fetch_catalog: bool = typer.Option(
        True,
        "--fetch-catalog/--no-fetch-catalog",
        help="Fetch component and Kamelet catalogs during init",
    ),
) -> None:
    """
    Initialize a new Camel-Kit project.

    Creates the project structure with AI agent commands for designing
    Apache Camel integrations.

    Examples:
        camel-kit init my-integration --ai bob
        camel-kit init --here --ai bob
    """
    # Show banner with camel
    show_banner(with_camel=True)

    # Validate agent
    if ai not in AGENT_CONFIG:
        console.print(f"[red]Error:[/red] Unknown agent '{ai}'")
        console.print(f"Available agents: {', '.join(get_agent_choices())}")
        raise typer.Exit(1)

    agent = AGENT_CONFIG[ai]

    # Resolve Camel version
    if camel_version == "latest":
        console.print("[dim]Detecting latest Camel version...[/dim]")
        camel_version = catalog_module.get_latest_camel_version()
        console.print(f"[green]✓[/green] Using Camel version {camel_version}")

    # Determine target directory
    if here:
        target_dir = Path.cwd()
        project_name = target_dir.name
    elif project_name:
        target_dir = Path.cwd() / project_name
    else:
        console.print("[red]Error:[/red] Please provide a project name or use --here")
        raise typer.Exit(1)

    # Check if directory exists
    if not here and target_dir.exists() and not force:
        console.print(f"[yellow]Warning:[/yellow] Directory '{target_dir}' already exists")
        if not typer.confirm("Continue and merge files?"):
            raise typer.Exit(0)

    # Create directories
    target_dir.mkdir(parents=True, exist_ok=True)
    commands_dir = target_dir / agent["folder"]
    commands_dir.mkdir(parents=True, exist_ok=True)
    camel_kit_dir = target_dir / ".camel-kit"
    camel_kit_dir.mkdir(parents=True, exist_ok=True)
    flows_dir = camel_kit_dir / "flows"
    flows_dir.mkdir(parents=True, exist_ok=True)
    test_dir = target_dir / "test"
    test_dir.mkdir(parents=True, exist_ok=True)
    test_data_dir = test_dir / "data"
    test_data_dir.mkdir(parents=True, exist_ok=True)

    # Copy templates
    templates_dir = get_templates_dir()
    files_created = []

    # Copy command files to agent directory
    commands_templates = templates_dir / "commands"
    if commands_templates.exists():
        for template_file in commands_templates.glob("*.md"):
            dest_file = commands_dir / f"camel.{template_file.stem}.md"
            content = template_file.read_text()
            # Substitute placeholders
            content = content.replace("{{CAMEL_VERSION}}", camel_version)
            content = content.replace("{{PROJECT_NAME}}", project_name)
            dest_file.write_text(content)
            files_created.append(str(dest_file.relative_to(target_dir)))

    # Copy flow template
    sdd_templates = ["flow.md"]
    for sdd_template in sdd_templates:
        template_file = templates_dir / sdd_template
        if template_file.exists():
            dest_file = camel_kit_dir / "templates" / sdd_template
            dest_file.parent.mkdir(parents=True, exist_ok=True)
            content = template_file.read_text()
            dest_file.write_text(content)
            files_created.append(str(dest_file.relative_to(target_dir)))

    # Copy constitution template
    constitution_template = templates_dir / "constitution.md"
    if constitution_template.exists():
        dest_file = camel_kit_dir / "constitution.md"
        content = constitution_template.read_text()
        content = content.replace("{{CAMEL_VERSION}}", camel_version)
        content = content.replace("{{DATE}}", "")  # Will be filled by user
        dest_file.write_text(content)
        files_created.append(str(dest_file.relative_to(target_dir)))

    # Copy context template (legacy support or global overview)
    context_template = templates_dir / "context.md"
    if context_template.exists():
        dest_file = camel_kit_dir / "context.md"
        content = context_template.read_text()
        content = content.replace("{{PROJECT_NAME}}", project_name)
        content = content.replace("{{CAMEL_VERSION}}", camel_version)
        dest_file.write_text(content)
        files_created.append(str(dest_file.relative_to(target_dir)))

    # Copy route template (legacy support)
    route_template = templates_dir / "route.md"
    if route_template.exists():
        dest_file = camel_kit_dir / "templates" / "route.md"
        dest_file.parent.mkdir(parents=True, exist_ok=True)
        content = route_template.read_text()
        dest_file.write_text(content)
        files_created.append(str(dest_file.relative_to(target_dir)))

    # Copy YAML generation guide
    yaml_guide = templates_dir / "yaml-generation-guide.md"
    if yaml_guide.exists():
        dest_file = camel_kit_dir / "templates" / "yaml-generation-guide.md"
        content = yaml_guide.read_text()
        dest_file.write_text(content)
        files_created.append(str(dest_file.relative_to(target_dir)))

    # Copy validation guide
    validation_guide = templates_dir / "validation-guide.md"
    if validation_guide.exists():
        dest_file = camel_kit_dir / "templates" / "validation-guide.md"
        content = validation_guide.read_text()
        dest_file.write_text(content)
        files_created.append(str(dest_file.relative_to(target_dir)))

    # Create config file
    config_content = f"""# Camel-Kit Configuration
project:
  name: {project_name}
  camelVersion: "{camel_version}"

agent:
  name: {ai}
  folder: {agent["folder"]}

catalog:
  # Will be populated when catalog is fetched
  lastFetched: null
"""
    config_file = camel_kit_dir / "config.yaml"
    config_file.write_text(config_content)
    files_created.append(str(config_file.relative_to(target_dir)))

    # Fetch component and Kamelet catalogs
    component_count = 0
    kamelet_count = 0
    if fetch_catalog:
        try:
            comp_catalog = catalog_module.fetch_component_catalog(camel_version, target_dir)
            component_count = comp_catalog.get("componentCount", 0)
        except Exception as e:
            console.print(f"[yellow]Warning:[/yellow] Could not fetch component catalog: {e}")
        try:
            kam_catalog = catalog_module.fetch_kamelet_catalog(camel_version, target_dir)
            kamelet_count = kam_catalog.get("kameletCount", 0)
        except Exception as e:
            console.print(f"[yellow]Warning:[/yellow] Could not fetch Kamelet catalog: {e}")

    # Success output
    console.print()
    success_msg = f"[green]✓[/green] Camel-Kit initialized for [bold]{project_name}[/bold]"
    if fetch_catalog and (component_count > 0 or kamelet_count > 0):
        success_msg += f"\n\n📦 Cached {component_count} components and {kamelet_count} Kamelets for Camel {camel_version}"
    elif not fetch_catalog:
        success_msg += f"\n\n[dim]Catalog not fetched (use 'camel-kit catalog fetch' when needed)[/dim]"
    console.print(Panel.fit(
        success_msg,
        title="Success",
        border_style="green",
    ))

    # Next steps
    console.print()
    console.print("[bold]Next steps:[/bold]")
    console.print(f"  1. Open [cyan]{project_name}[/cyan] in {agent['name']}")
    console.print("  2. Run [cyan]/camel.context[/cyan] to define your integration landscape [dim](optional)[/dim]")
    console.print("  3. Run [cyan]/camel.flow <flow-name>[/cyan] to define and design a flow")
    console.print("  4. Run [cyan]/camel.implement <flow-name>[/cyan] to generate the Camel YAML")
    console.print()


@app.command()
def agents() -> None:
    """
    List available AI coding agents.
    """
    table = Table(title="Available AI Agents")
    table.add_column("Agent", style="cyan")
    table.add_column("Name", style="green")
    table.add_column("Commands Folder")
    table.add_column("Status")

    for key, config in AGENT_CONFIG.items():
        status = "[green]Available[/green]"
        table.add_row(
            key,
            config["name"],
            config["folder"],
            status,
        )

    console.print(table)
    console.print()
    console.print("Use [cyan]camel-kit init <project> --ai <agent>[/cyan] to initialize")


@app.command()
def catalog(
    action: str = typer.Argument(
        "info",
        help="Action: info, fetch, search",
    ),
    query: Optional[str] = typer.Argument(
        None,
        help="Search query (for 'search' action)",
    ),
    camel_version: str = typer.Option(
        "latest",
        "--camel-version",
        "-v",
        help="Apache Camel version",
    ),
    force: bool = typer.Option(
        False,
        "--force",
        "-f",
        help="Force refresh even if cached",
    ),
    kamelet_type: Optional[str] = typer.Option(
        None,
        "--type",
        "-t",
        help="Filter Kamelets by type: source, sink, action",
    ),
) -> None:
    """
    Manage Camel component and Kamelet catalogs.

    Actions:
        info    - Show catalog status and stats
        fetch   - Download/refresh catalogs
        search  - Search components and Kamelets

    Examples:
        camel-kit catalog info
        camel-kit catalog fetch --camel-version 4.10.0
        camel-kit catalog search kafka
        camel-kit catalog search postgres --type sink
    """
    project_dir = Path.cwd()
    camel_kit_dir = project_dir / ".camel-kit"

    # Resolve version
    if camel_version == "latest":
        camel_version = catalog_module.get_latest_camel_version()

    if action == "info":
        # Show catalog status
        cache_dir = camel_kit_dir / ".cache"
        comp_cache = cache_dir / f"components-{camel_version}.json"
        kam_cache = cache_dir / f"kamelets-{camel_version.replace('.', '_')}.json"

        console.print(Panel.fit(f"Catalog Status (Camel {camel_version})", border_style="blue"))

        if comp_cache.exists():
            import json
            data = json.loads(comp_cache.read_text())
            console.print(f"[green]✓[/green] Components: {data.get('componentCount', 0)} cached")
            console.print(f"  [dim]Fetched: {data.get('fetchedAt', 'unknown')}[/dim]")
        else:
            console.print("[yellow]○[/yellow] Components: not cached")

        if kam_cache.exists():
            import json
            data = json.loads(kam_cache.read_text())
            console.print(f"[green]✓[/green] Kamelets: {data.get('kameletCount', 0)} cached")
            console.print(f"  [dim]Fetched: {data.get('fetchedAt', 'unknown')}[/dim]")
        else:
            console.print("[yellow]○[/yellow] Kamelets: not cached")

        console.print()
        console.print("Use [cyan]camel-kit catalog fetch[/cyan] to download catalogs")

    elif action == "fetch":
        # Fetch catalogs
        console.print(f"Fetching catalogs for Camel {camel_version}...")
        console.print()

        try:
            comp_catalog = catalog_module.fetch_component_catalog(
                camel_version, project_dir, force_refresh=force
            )
            console.print(
                f"[green]✓[/green] Components: {comp_catalog.get('componentCount', 0)}"
            )
        except Exception as e:
            console.print(f"[red]✗[/red] Components: {e}")

        try:
            kam_catalog = catalog_module.fetch_kamelet_catalog(
                camel_version, project_dir, force_refresh=force
            )
            console.print(f"[green]✓[/green] Kamelets: {kam_catalog.get('kameletCount', 0)}")
        except Exception as e:
            console.print(f"[red]✗[/red] Kamelets: {e}")

    elif action == "search":
        if not query:
            console.print("[red]Error:[/red] Please provide a search query")
            console.print("Example: camel-kit catalog search kafka")
            raise typer.Exit(1)

        # Load catalogs
        cache_dir = camel_kit_dir / ".cache"
        comp_cache = cache_dir / f"components-{camel_version}.json"
        kam_cache = cache_dir / f"kamelets-{camel_version.replace('.', '_')}.json"

        results_found = False

        # Search components
        if comp_cache.exists():
            import json
            comp_catalog = json.loads(comp_cache.read_text())
            results = catalog_module.search_components(query, comp_catalog, limit=5)

            if results:
                results_found = True
                console.print()
                console.print("[bold]Components:[/bold]")
                table = Table(show_header=True)
                table.add_column("Name", style="cyan")
                table.add_column("Title")
                table.add_column("Description")

                for r in results:
                    comp_info = r.get("component", {})
                    table.add_row(
                        r["name"],
                        comp_info.get("title", ""),
                        (comp_info.get("description", "")[:60] + "...")
                        if len(comp_info.get("description", "")) > 60
                        else comp_info.get("description", ""),
                    )
                console.print(table)

        # Search Kamelets
        if kam_cache.exists():
            import json
            kam_catalog = json.loads(kam_cache.read_text())
            results = catalog_module.search_kamelets(
                query, kam_catalog, kamelet_type=kamelet_type, limit=5
            )

            if results:
                results_found = True
                console.print()
                console.print("[bold]Kamelets:[/bold]")
                table = Table(show_header=True)
                table.add_column("Name", style="cyan")
                table.add_column("Type", style="green")
                table.add_column("Title")

                for r in results:
                    table.add_row(
                        r["name"],
                        r.get("type", ""),
                        r.get("title", ""),
                    )
                console.print(table)

        if not results_found:
            console.print(f"[yellow]No results found for '{query}'[/yellow]")
            console.print("Try [cyan]camel-kit catalog fetch[/cyan] first if catalogs are not cached")

    else:
        console.print(f"[red]Error:[/red] Unknown action '{action}'")
        console.print("Available actions: info, fetch, search")
        raise typer.Exit(1)


@app.command()
def version() -> None:
    """
    Show Camel-Kit version.
    """
    show_banner(with_camel=True)
    console.print(f"[bold]camel-kit-cli[/bold] version [cyan]{__version__}[/cyan]")
    console.print()
    console.print("[dim]https://github.com/luigidemasi/camel-kit[/dim]")


def main() -> None:
    """Main entry point."""
    app()


if __name__ == "__main__":
    main()
