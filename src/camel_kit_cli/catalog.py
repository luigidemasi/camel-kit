"""
Camel Catalog Fetching and Caching.

This module handles:
- Fetching component catalog from Maven Central (version-specific)
- Fetching Kamelet catalog from GitHub (version-specific)
- Local caching in .camel-kit/.cache/
- Version detection (latest LTS)
"""

import json
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Any

import httpx
from rich.console import Console
from rich.progress import Progress, SpinnerColumn, TextColumn

console = Console()

# Maven Central base URL
MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2"

# GitHub API for Kamelets
GITHUB_API_URL = "https://api.github.com"
KAMELETS_REPO = "apache/camel-kamelets"
KAMELETS_RAW_URL = "https://raw.githubusercontent.com/apache/camel-kamelets"

# Cache settings
CACHE_DIR_NAME = ".cache"
CACHE_EXPIRY_HOURS = 24


def get_cache_dir(project_dir: Path) -> Path:
    """Get the cache directory for a project."""
    cache_dir = project_dir / ".camel-kit" / CACHE_DIR_NAME
    cache_dir.mkdir(parents=True, exist_ok=True)
    return cache_dir


def is_cache_valid(cache_file: Path, max_age_hours: int = CACHE_EXPIRY_HOURS) -> bool:
    """Check if a cache file exists and is not expired."""
    if not cache_file.exists():
        return False

    mtime = datetime.fromtimestamp(cache_file.stat().st_mtime, tz=timezone.utc)
    age_hours = (datetime.now(tz=timezone.utc) - mtime).total_seconds() / 3600
    return age_hours < max_age_hours


def get_latest_camel_version() -> str:
    """
    Fetch the latest LTS Camel version from Maven Central.

    Returns the latest 4.x LTS version.
    """
    try:
        # Query Maven Central for latest version
        url = f"{MAVEN_CENTRAL_URL}/org/apache/camel/camel-catalog/maven-metadata.xml"
        response = httpx.get(url, timeout=30)
        response.raise_for_status()

        # Parse XML to find latest version
        # Simple regex parsing to avoid XML dependency
        import re

        versions = re.findall(r"<version>(\d+\.\d+\.\d+)</version>", response.text)

        # Filter to 4.x versions and get latest
        v4_versions = [v for v in versions if v.startswith("4.")]
        if v4_versions:
            # Sort by version number
            v4_versions.sort(key=lambda v: [int(x) for x in v.split(".")], reverse=True)
            return v4_versions[0]

        # Fallback
        return "4.10.0"

    except Exception as e:
        console.print(f"[yellow]Warning:[/yellow] Could not fetch latest version: {e}")
        return "4.10.0"  # Fallback to known LTS


def fetch_component_catalog(
    version: str,
    project_dir: Path,
    force_refresh: bool = False,
) -> dict[str, Any]:
    """
    Fetch the Camel component catalog for a specific version.

    Downloads the camel-catalog JAR from Maven Central and extracts
    component JSON files.

    Args:
        version: Camel version (e.g., "4.10.0")
        project_dir: Project directory for caching
        force_refresh: Force re-download even if cached

    Returns:
        Dictionary with component metadata
    """
    cache_dir = get_cache_dir(project_dir)
    cache_file = cache_dir / f"components-{version}.json"

    # Check cache
    if not force_refresh and is_cache_valid(cache_file):
        console.print(f"[dim]Using cached component catalog (v{version})[/dim]")
        return json.loads(cache_file.read_text())

    # Download JAR from Maven Central
    jar_url = (
        f"{MAVEN_CENTRAL_URL}/org/apache/camel/camel-catalog/{version}/"
        f"camel-catalog-{version}.jar"
    )

    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        console=console,
    ) as progress:
        task = progress.add_task(f"Fetching component catalog (v{version})...", total=None)

        try:
            response = httpx.get(jar_url, timeout=60, follow_redirects=True)
            response.raise_for_status()
        except httpx.HTTPError as e:
            console.print(f"[red]Error:[/red] Failed to download catalog: {e}")
            raise

        progress.update(task, description="Extracting component metadata...")

        # Extract component JSONs from JAR
        components = {}
        jar_bytes = BytesIO(response.content)

        with zipfile.ZipFile(jar_bytes, "r") as jar:
            for name in jar.namelist():
                # Component JSONs are in org/apache/camel/catalog/components/
                if name.startswith("org/apache/camel/catalog/components/") and name.endswith(
                    ".json"
                ):
                    component_name = Path(name).stem
                    try:
                        content = jar.read(name)
                        component_data = json.loads(content)
                        components[component_name] = component_data
                    except (json.JSONDecodeError, KeyError):
                        continue

        progress.update(task, description=f"Cached {len(components)} components")

    # Build catalog structure
    catalog = {
        "version": version,
        "fetchedAt": datetime.now(tz=timezone.utc).isoformat(),
        "componentCount": len(components),
        "components": components,
    }

    # Save to cache
    cache_file.write_text(json.dumps(catalog, indent=2))
    console.print(f"[green]✓[/green] Cached {len(components)} components (v{version})")

    return catalog


def fetch_kamelet_catalog(
    version: str | None,
    project_dir: Path,
    force_refresh: bool = False,
) -> dict[str, Any]:
    """
    Fetch the Kamelet catalog from GitHub.

    Args:
        version: Camel version for tag matching (e.g., "4.10.0"), or None for main
        project_dir: Project directory for caching
        force_refresh: Force re-download even if cached

    Returns:
        Dictionary with Kamelet metadata
    """
    cache_dir = get_cache_dir(project_dir)
    version_tag = version or "main"
    cache_file = cache_dir / f"kamelets-{version_tag.replace('.', '_')}.json"

    # Check cache
    if not force_refresh and is_cache_valid(cache_file):
        console.print(f"[dim]Using cached Kamelet catalog ({version_tag})[/dim]")
        return json.loads(cache_file.read_text())

    # Determine Git ref (tag or branch)
    # Kamelet versions follow pattern: v4.10.0, v4.9.0, etc.
    git_ref = f"v{version}" if version else "main"

    # First, try to list Kamelets from GitHub API
    api_url = f"{GITHUB_API_URL}/repos/{KAMELETS_REPO}/contents/kamelets?ref={git_ref}"

    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        console=console,
    ) as progress:
        task = progress.add_task(f"Fetching Kamelet catalog ({version_tag})...", total=None)

        try:
            # Get list of Kamelet files
            response = httpx.get(
                api_url,
                timeout=30,
                headers={"Accept": "application/vnd.github.v3+json"},
            )

            # If version tag doesn't exist, fall back to main
            if response.status_code == 404 and version:
                console.print(
                    f"[yellow]Warning:[/yellow] Tag v{version} not found, using main branch"
                )
                git_ref = "main"
                api_url = f"{GITHUB_API_URL}/repos/{KAMELETS_REPO}/contents/kamelets?ref=main"
                response = httpx.get(
                    api_url,
                    timeout=30,
                    headers={"Accept": "application/vnd.github.v3+json"},
                )

            response.raise_for_status()
            files = response.json()

        except httpx.HTTPError as e:
            console.print(f"[red]Error:[/red] Failed to fetch Kamelet list: {e}")
            raise

        # Filter to .kamelet.yaml files
        kamelet_files = [f for f in files if f["name"].endswith(".kamelet.yaml")]

        progress.update(
            task, description=f"Downloading {len(kamelet_files)} Kamelets...", total=None
        )

        kamelets = {}

        def fetch_single_kamelet(kamelet_file: dict) -> tuple[str, dict | None]:
            """Fetch a single Kamelet file and parse it."""
            kamelet_name = kamelet_file["name"].replace(".kamelet.yaml", "")
            try:
                raw_url = f"{KAMELETS_RAW_URL}/{git_ref}/kamelets/{kamelet_file['name']}"
                kamelet_response = httpx.get(raw_url, timeout=15)
                kamelet_response.raise_for_status()
                content = kamelet_response.text
                kamelet_data = parse_kamelet_yaml(content, kamelet_name)
                return kamelet_name, kamelet_data
            except Exception:
                return kamelet_name, None

        # Parallel download with ThreadPoolExecutor
        max_workers = min(20, len(kamelet_files))  # Limit concurrent connections
        completed = 0
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {
                executor.submit(fetch_single_kamelet, kf): kf for kf in kamelet_files
            }
            for future in as_completed(futures):
                kamelet_name, kamelet_data = future.result()
                if kamelet_data:
                    kamelets[kamelet_name] = kamelet_data
                completed += 1
                if completed % 20 == 0:
                    progress.update(
                        task,
                        description=f"Downloading Kamelets ({completed}/{len(kamelet_files)})...",
                    )

        progress.update(task, description=f"Cached {len(kamelets)} Kamelets")

    # Build catalog structure
    catalog = {
        "version": version_tag,
        "gitRef": git_ref,
        "fetchedAt": datetime.now(tz=timezone.utc).isoformat(),
        "kameletCount": len(kamelets),
        "kamelets": kamelets,
    }

    # Save to cache
    cache_file.write_text(json.dumps(catalog, indent=2))
    console.print(f"[green]✓[/green] Cached {len(kamelets)} Kamelets ({version_tag})")

    return catalog


def parse_kamelet_yaml(content: str, name: str) -> dict[str, Any]:
    """
    Parse a Kamelet YAML file to extract key metadata.

    Simple parsing without full YAML library dependency.
    Extracts: title, description, type (source/sink/action), required properties.
    """
    kamelet: dict[str, Any] = {
        "name": name,
        "title": name,
        "description": "",
        "type": "action",
        "properties": {},
        "required": [],
    }

    lines = content.split("\n")
    in_spec = False
    in_definition = False
    in_properties = False
    current_property = None
    indent_level = 0

    for line in lines:
        stripped = line.strip()

        # Track sections
        if stripped.startswith("spec:"):
            in_spec = True
            continue

        if in_spec and stripped.startswith("definition:"):
            in_definition = True
            continue

        # Extract title
        if in_definition and stripped.startswith("title:"):
            kamelet["title"] = stripped.split(":", 1)[1].strip().strip('"\'')

        # Extract description
        if in_definition and stripped.startswith("description:"):
            kamelet["description"] = stripped.split(":", 1)[1].strip().strip('"\'')

        # Detect type from labels or name
        if "camel.apache.org/kamelet.type:" in stripped:
            type_value = stripped.split(":", 1)[1].strip().strip('"\'')
            kamelet["type"] = type_value

        # Extract properties section
        if in_definition and stripped.startswith("properties:"):
            in_properties = True
            continue

        if in_properties:
            # Check if we're still in properties (by indentation)
            if stripped and not line.startswith(" " * 6) and not line.startswith("\t"):
                if not stripped.startswith("#"):
                    in_properties = False

            # Property name (at correct indent level)
            if line.startswith(" " * 8) and stripped.endswith(":") and not stripped.startswith("#"):
                current_property = stripped[:-1]
                kamelet["properties"][current_property] = {
                    "title": current_property,
                    "description": "",
                    "type": "string",
                }

            # Property attributes
            if current_property and line.startswith(" " * 10):
                if stripped.startswith("title:"):
                    kamelet["properties"][current_property]["title"] = (
                        stripped.split(":", 1)[1].strip().strip('"\'')
                    )
                elif stripped.startswith("description:"):
                    kamelet["properties"][current_property]["description"] = (
                        stripped.split(":", 1)[1].strip().strip('"\'')
                    )
                elif stripped.startswith("type:"):
                    kamelet["properties"][current_property]["type"] = (
                        stripped.split(":", 1)[1].strip().strip('"\'')
                    )

        # Extract required properties
        if in_definition and stripped.startswith("required:"):
            # Handle inline array
            if "[" in stripped:
                import re

                required = re.findall(r'"([^"]+)"', stripped)
                kamelet["required"] = required

    # Infer type from name if not found
    if kamelet["type"] == "action":
        if name.endswith("-source"):
            kamelet["type"] = "source"
        elif name.endswith("-sink"):
            kamelet["type"] = "sink"

    return kamelet


def get_component_info(
    component_name: str,
    catalog: dict[str, Any],
) -> dict[str, Any] | None:
    """
    Get component information from the catalog.

    Returns component metadata including options, or None if not found.
    """
    return catalog.get("components", {}).get(component_name)


def get_kamelet_info(
    kamelet_name: str,
    catalog: dict[str, Any],
) -> dict[str, Any] | None:
    """
    Get Kamelet information from the catalog.

    Returns Kamelet metadata including properties, or None if not found.
    """
    return catalog.get("kamelets", {}).get(kamelet_name)


def search_components(
    query: str,
    catalog: dict[str, Any],
    limit: int = 10,
) -> list[dict[str, Any]]:
    """
    Search components by name or description.

    Returns list of matching components.
    """
    results = []
    query_lower = query.lower()

    for name, component in catalog.get("components", {}).items():
        # Search in name
        if query_lower in name.lower():
            results.append({"name": name, "match": "name", **component})
            continue

        # Search in title/description
        comp_info = component.get("component", {})
        title = comp_info.get("title", "").lower()
        description = comp_info.get("description", "").lower()

        if query_lower in title or query_lower in description:
            results.append({"name": name, "match": "description", **component})

    return results[:limit]


def search_kamelets(
    query: str,
    catalog: dict[str, Any],
    kamelet_type: str | None = None,
    limit: int = 10,
) -> list[dict[str, Any]]:
    """
    Search Kamelets by name, description, or type.

    Args:
        query: Search term
        catalog: Kamelet catalog
        kamelet_type: Filter by type (source/sink/action)
        limit: Maximum results

    Returns list of matching Kamelets.
    """
    results = []
    query_lower = query.lower()

    for name, kamelet in catalog.get("kamelets", {}).items():
        # Filter by type if specified
        if kamelet_type and kamelet.get("type") != kamelet_type:
            continue

        # Search in name
        if query_lower in name.lower():
            results.append({"name": name, "match": "name", **kamelet})
            continue

        # Search in title/description
        title = kamelet.get("title", "").lower()
        description = kamelet.get("description", "").lower()

        if query_lower in title or query_lower in description:
            results.append({"name": name, "match": "description", **kamelet})

    return results[:limit]
