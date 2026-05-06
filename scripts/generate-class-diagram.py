import argparse
import os
import re
import subprocess
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = REPO_ROOT / "software" / "src" / "main" / "java"
PLANTUML_DIR = REPO_ROOT / "docs" / "class-diagrams" / "plantuml-diagrams"
PNG_DIR = REPO_ROOT / "docs" / "class-diagrams"
SUMMARY_OUTPUT_FILE = PLANTUML_DIR / "class-diagram-summary.puml"
PACKAGE_OUTPUT_TEMPLATE = "class-diagram-{package_name}.puml"
PROJECT_PACKAGE_PREFIX = "edu.sjsu.spring2026.group32"
EXCLUDED_TOP_LEVEL_PACKAGES = {"annotations"}
SUMMARY_GROUPS = {
    "core-suite": {"hardware", "player"},
    "application-suite": {"launcher", "bidirectionaltest", "hitthezone", "pong"},
}
SUMMARY_TIERS = [
    ["core-suite"],
    ["application-suite"],
]
SUMMARY_LAUNCHER_TARGETS = ("bidirectionaltest", "hitthezone", "pong")

PACKAGE_PATTERN = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
IMPORT_PATTERN = re.compile(r"^\s*import\s+([\w.]+)\s*;", re.MULTILINE)
TYPE_PATTERN = re.compile(
    r"^\s*(?:public\s+)?(?:abstract\s+|final\s+)?(@interface|class|interface|enum|record)\s+([A-Z]\w*)",
    re.MULTILINE,
)
EXTENDS_PATTERN = re.compile(r"\bextends\s+([A-Z]\w*)")
IMPLEMENTS_PATTERN = re.compile(r"\bimplements\s+([A-Z][\w\s,<>]*)")
NEW_PATTERN = re.compile(r"\bnew\s+([A-Z]\w*)\s*\(")

LINE_COMMENT_PATTERN = re.compile(r"//.*?$", re.MULTILINE)
BLOCK_COMMENT_PATTERN = re.compile(r"/\*.*?\*/", re.DOTALL)
STRING_PATTERN = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_PATTERN = re.compile(r"'(?:\\.|[^'\\])'")


@dataclass(frozen=True)
class JavaType:
    simple_name: str
    full_name: str
    package_name: str
    relative_package_name: str
    top_level_package: str
    subpackage_name: str
    kind: str
    alias: str


@dataclass(frozen=True)
class PackageNode:
    full_package_name: str
    top_level_package: str
    subpackage_name: str
    alias: str


def strip_non_code(content: str) -> str:
    without_block_comments = BLOCK_COMMENT_PATTERN.sub("", content)
    without_line_comments = LINE_COMMENT_PATTERN.sub("", without_block_comments)
    without_strings = STRING_PATTERN.sub('""', without_line_comments)
    return CHAR_PATTERN.sub("''", without_strings)


def alias_for(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9_]", "_", name)


def package_alias_for(name: str) -> str:
    return "pkg_" + alias_for(name)


def kind_for_plantuml(kind: str) -> str:
    if kind == "@interface":
        return "annotation"
    if kind == "record":
        return "class"
    return kind


def stereotype_for(kind: str) -> str:
    return " <<record>>" if kind == "record" else ""


def declaration_header(content: str, match: re.Match[str]) -> str:
    start = match.start()
    brace_index = content.find("{", start)
    if brace_index == -1:
        return content[start:start + 400]
    return content[start:brace_index]


def remove_generic_blocks(text: str) -> str:
    previous = None
    current = text
    while previous != current:
        previous = current
        current = re.sub(r"<[^<>]*>", "", current)
    return current


def relative_package_name(package_name: str) -> str | None:
    prefix = PROJECT_PACKAGE_PREFIX + "."
    if package_name.startswith(prefix):
        return package_name[len(prefix):]
    return None


def split_package_family(package_name: str) -> tuple[str, str] | None:
    relative_name = relative_package_name(package_name)
    if not relative_name:
        return None
    parts = relative_name.split(".")
    top_level = parts[0]
    if top_level in EXCLUDED_TOP_LEVEL_PACKAGES:
        return None
    return top_level, ".".join(parts[1:])


def resolve_local_type(type_name: str, package_name: str, imports: dict[str, str]) -> str:
    imported = imports.get(type_name)
    if imported:
        return imported
    return f"{package_name}.{type_name}" if package_name else type_name


def render_package_block(
    lines: list[str],
    types_by_subpackage: dict[str, list[JavaType]],
    top_level_package: str,
    package_nodes: dict[tuple[str, str], PackageNode],
) -> None:
    top_level_node = package_nodes[(top_level_package, "")]
    lines.append(f'package "{top_level_package}" as {top_level_node.alias} {{')

    root_types = types_by_subpackage.get("", [])
    for java_type in root_types:
        lines.append(
            f'  {kind_for_plantuml(java_type.kind)} "{java_type.simple_name}" as {java_type.alias}{stereotype_for(java_type.kind)}'
        )

    for subpackage_name in sorted(name for name in types_by_subpackage if name):
        subpackage_node = package_nodes[(top_level_package, subpackage_name)]
        lines.append(f'  package "{subpackage_name}" as {subpackage_node.alias} {{')
        for java_type in types_by_subpackage[subpackage_name]:
            lines.append(
                f'    {kind_for_plantuml(java_type.kind)} "{java_type.simple_name}" as {java_type.alias}{stereotype_for(java_type.kind)}'
            )
        lines.append("  }")

    lines.append("}")
    lines.append("")


def build_model() -> tuple[
    dict[str, JavaType],
    set[tuple[str, str]],
    set[tuple[str, str]],
    set[tuple[str, str]],
    dict[str, dict[str, list[JavaType]]],
    dict[tuple[str, str], PackageNode],
]:
    types_by_full_name: dict[str, JavaType] = {}
    imports_by_type: dict[str, dict[str, str]] = {}
    content_by_type: dict[str, str] = {}

    for java_file in sorted(SOURCE_DIR.rglob("*.java")):
        raw_content = java_file.read_text(encoding="utf-8", errors="ignore")
        content = strip_non_code(raw_content)

        package_match = PACKAGE_PATTERN.search(content)
        package_name = package_match.group(1) if package_match else ""
        package_split = split_package_family(package_name)
        if not package_split:
            continue

        type_match = TYPE_PATTERN.search(content)
        if not type_match:
            continue

        top_level_package, subpackage_name = package_split
        relative_name = relative_package_name(package_name)
        if relative_name is None:
            continue

        kind = type_match.group(1)
        simple_name = type_match.group(2)
        full_name = f"{package_name}.{simple_name}"

        imports: dict[str, str] = {}
        for imported_name in IMPORT_PATTERN.findall(content):
            imported_split = split_package_family(imported_name.rsplit(".", 1)[0])
            if imported_split is None:
                continue
            imports[imported_name.rsplit(".", 1)[-1]] = imported_name

        types_by_full_name[full_name] = JavaType(
            simple_name=simple_name,
            full_name=full_name,
            package_name=package_name,
            relative_package_name=relative_name,
            top_level_package=top_level_package,
            subpackage_name=subpackage_name,
            kind=kind,
            alias=alias_for(full_name),
        )
        imports_by_type[full_name] = imports
        content_by_type[full_name] = content

    full_names = set(types_by_full_name)
    extends_relations: set[tuple[str, str]] = set()
    implements_relations: set[tuple[str, str]] = set()
    usage_relations: set[tuple[str, str]] = set()

    for full_name, java_type in types_by_full_name.items():
        content = content_by_type[full_name]
        imports = imports_by_type[full_name]
        header_match = TYPE_PATTERN.search(content)
        if not header_match:
            continue

        header = remove_generic_blocks(declaration_header(content, header_match))

        extends_match = EXTENDS_PATTERN.search(header)
        if extends_match:
            resolved_parent = resolve_local_type(extends_match.group(1), java_type.package_name, imports)
            if resolved_parent in full_names and resolved_parent != full_name:
                extends_relations.add((full_name, resolved_parent))

        implements_match = IMPLEMENTS_PATTERN.search(header)
        if implements_match:
            for interface_name in implements_match.group(1).split(","):
                simple_name = interface_name.strip().split("<", 1)[0].strip()
                resolved_interface = resolve_local_type(simple_name, java_type.package_name, imports)
                if resolved_interface in full_names and resolved_interface != full_name:
                    implements_relations.add((full_name, resolved_interface))

        for imported_simple_name, imported_full_name in imports.items():
            if imported_full_name == full_name or imported_full_name not in full_names:
                continue
            if re.search(rf"\b{re.escape(imported_simple_name)}\b", content):
                usage_relations.add((full_name, imported_full_name))

        for new_type_name in NEW_PATTERN.findall(content):
            resolved_new_type = resolve_local_type(new_type_name, java_type.package_name, imports)
            if resolved_new_type in full_names and resolved_new_type != full_name:
                usage_relations.add((full_name, resolved_new_type))

    usage_relations -= extends_relations
    usage_relations -= implements_relations

    top_level_packages = sorted({java_type.top_level_package for java_type in types_by_full_name.values()})
    families: dict[str, dict[str, list[JavaType]]] = {
        top_level: defaultdict(list) for top_level in top_level_packages
    }
    for java_type in sorted(types_by_full_name.values(), key=lambda item: item.full_name):
        families[java_type.top_level_package][java_type.subpackage_name].append(java_type)

    package_nodes: dict[tuple[str, str], PackageNode] = {}
    for top_level in top_level_packages:
        root_package_name = f"{PROJECT_PACKAGE_PREFIX}.{top_level}"
        package_nodes[(top_level, "")] = PackageNode(
            full_package_name=root_package_name,
            top_level_package=top_level,
            subpackage_name="",
            alias=package_alias_for(root_package_name),
        )
        for subpackage_name in sorted(name for name in families[top_level] if name):
            full_package_name = f"{root_package_name}.{subpackage_name}"
            package_nodes[(top_level, subpackage_name)] = PackageNode(
                full_package_name=full_package_name,
                top_level_package=top_level,
                subpackage_name=subpackage_name,
                alias=package_alias_for(full_package_name),
            )

    return (
        types_by_full_name,
        extends_relations,
        implements_relations,
        usage_relations,
        families,
        package_nodes,
    )


def base_lines(title: str, *, linetype: str = "ortho") -> list[str]:
    return [
        "@startuml",
        f"title {title}",
        "top to bottom direction",
        "skinparam classAttributeIconSize 0",
        "skinparam packageStyle rectangle",
        f"skinparam linetype {linetype}",
        "hide empty members",
        "",
    ]


def ordered_summary_tiers(top_level_packages: list[str]) -> list[list[str]]:
    available_groups = {summary_group_name(package_name) for package_name in top_level_packages}
    ordered: list[list[str]] = []

    for tier in SUMMARY_TIERS:
        active = [package_name for package_name in tier if package_name in available_groups]
        if active:
            ordered.append(active)
            available_groups -= set(active)

    if available_groups:
        ordered.append(sorted(available_groups))

    return ordered


def summary_group_name(package_name: str) -> str:
    for group_name, members in SUMMARY_GROUPS.items():
        if package_name in members:
            return group_name
    return package_name


def write_summary_diagram(
    families: dict[str, dict[str, list[JavaType]]],
    package_nodes: dict[tuple[str, str], PackageNode],
    usage_relations: set[tuple[str, str]],
    types_by_full_name: dict[str, JavaType],
) -> None:
    lines = base_lines("Package Architecture Summary Diagram", linetype="polyline")
    top_level_packages = sorted(families)
    tiers = ordered_summary_tiers(top_level_packages)
    summary_group_aliases = {
        "core-suite": "summary_group_core_suite",
        "application-suite": "summary_group_application_suite",
    }
    tier_relations: set[tuple[str, str]] = set()

    for tier in tiers:
        lines.append("together {")
        for package_name in tier:
            if package_name in SUMMARY_GROUPS:
                lines.append(f'  package "{package_name}" as {summary_group_aliases[package_name]} {{')
                for member_name in sorted(SUMMARY_GROUPS[package_name]):
                    if member_name not in families:
                        continue
                    node = package_nodes[(member_name, "")]
                    lines.append(f'    package "{member_name}" as {node.alias} {{')
                    for java_type in families[member_name].get("", []):
                        lines.append(
                            f'      {kind_for_plantuml(java_type.kind)} "{java_type.simple_name}" as {java_type.alias}{stereotype_for(java_type.kind)}'
                        )
                    for subpackage_name in sorted(name for name in families[member_name] if name):
                        subpackage_node = package_nodes[(member_name, subpackage_name)]
                        lines.append(f'      package "{subpackage_name}" as {subpackage_node.alias} {{}}')
                    lines.append("    }")
                lines.append("  }")
            else:
                node = package_nodes[(package_name, "")]
                lines.append(f'  package "{package_name}" as {node.alias} {{')
                for java_type in families[package_name].get("", []):
                    lines.append(
                        f'    {kind_for_plantuml(java_type.kind)} "{java_type.simple_name}" as {java_type.alias}{stereotype_for(java_type.kind)}'
                    )
                for subpackage_name in sorted(name for name in families[package_name] if name):
                    subpackage_node = package_nodes[(package_name, subpackage_name)]
                    lines.append(f'    package "{subpackage_name}" as {subpackage_node.alias} {{}}')
                lines.append("  }")
        lines.append("}")
        lines.append("")

    for upper_tier, lower_tier in zip(tiers, tiers[1:]):
        for upper_name in upper_tier:
            for lower_name in lower_tier:
                upper_alias = summary_group_aliases[upper_name] if upper_name in summary_group_aliases else package_nodes[(upper_name, "")].alias
                lower_alias = summary_group_aliases[lower_name] if lower_name in summary_group_aliases else package_nodes[(lower_name, "")].alias
                tier_relations.add((lower_alias, upper_alias))
                lines.append(f"{lower_alias} ..up> {upper_alias}")
        lines.append("")

    top_level_package_relations: set[tuple[str, str]] = set()
    for source, target in sorted(usage_relations):
        source_type = types_by_full_name[source]
        target_type = types_by_full_name[target]
        if source_type.top_level_package != target_type.top_level_package:
            top_level_package_relations.add((
                summary_group_name(source_type.top_level_package),
                summary_group_name(target_type.top_level_package),
            ))

    for source_top_level, target_top_level in sorted(top_level_package_relations):
        if source_top_level == target_top_level:
            continue
        source_alias = summary_group_aliases[source_top_level] if source_top_level in summary_group_aliases else package_nodes[(source_top_level, "")].alias
        target_alias = summary_group_aliases[target_top_level] if target_top_level in summary_group_aliases else package_nodes[(target_top_level, "")].alias
        if (source_alias, target_alias) in tier_relations:
            continue
        lines.append(
            f"{source_alias} ..> {target_alias}"
        )

    if "launcher" in families:
        launcher_alias = package_nodes[("launcher", "")].alias
        for target_name in SUMMARY_LAUNCHER_TARGETS:
            if target_name in families:
                target_alias = package_nodes[(target_name, "")].alias
                lines.append(f"{launcher_alias} ..> {target_alias}")

    lines.append("")
    lines.append("@enduml")
    lines.append("")
    SUMMARY_OUTPUT_FILE.write_text("\n".join(lines), encoding="utf-8")


def write_package_diagram(
    top_level_package: str,
    families: dict[str, dict[str, list[JavaType]]],
    package_nodes: dict[tuple[str, str], PackageNode],
    types_by_full_name: dict[str, JavaType],
    extends_relations: set[tuple[str, str]],
    implements_relations: set[tuple[str, str]],
    usage_relations: set[tuple[str, str]],
) -> None:
    lines = base_lines(f"{top_level_package} Class Diagram")
    render_package_block(lines, families[top_level_package], top_level_package, package_nodes)

    type_aliases = {
        full_name: java_type.alias
        for full_name, java_type in types_by_full_name.items()
        if java_type.top_level_package == top_level_package
    }
    root_to_subpackage_relations: set[tuple[str, str, str]] = set()

    def same_family(source_name: str, target_name: str) -> bool:
        return (
            types_by_full_name[source_name].top_level_package == top_level_package
            and types_by_full_name[target_name].top_level_package == top_level_package
        )

    for child, parent in sorted(extends_relations):
        if not same_family(child, parent):
            continue
        if types_by_full_name[child].package_name == types_by_full_name[parent].package_name:
            lines.append(f"{type_aliases[parent]} <|-- {type_aliases[child]}")
        elif types_by_full_name[child].subpackage_name == "" and types_by_full_name[parent].subpackage_name != "":
            root_to_subpackage_relations.add((child, top_level_package, types_by_full_name[parent].subpackage_name))

    for child, interface in sorted(implements_relations):
        if not same_family(child, interface):
            continue
        if types_by_full_name[child].package_name == types_by_full_name[interface].package_name:
            lines.append(f"{type_aliases[interface]} <|.. {type_aliases[child]}")
        elif types_by_full_name[child].subpackage_name == "" and types_by_full_name[interface].subpackage_name != "":
            root_to_subpackage_relations.add((child, top_level_package, types_by_full_name[interface].subpackage_name))

    for source, target in sorted(usage_relations):
        if not same_family(source, target):
            continue
        if types_by_full_name[source].package_name == types_by_full_name[target].package_name:
            lines.append(f"{type_aliases[source]} ..> {type_aliases[target]}")
        elif types_by_full_name[source].subpackage_name == "" and types_by_full_name[target].subpackage_name != "":
            root_to_subpackage_relations.add((source, top_level_package, types_by_full_name[target].subpackage_name))

    for source, package_family, subpackage_name in sorted(root_to_subpackage_relations):
        target_node = package_nodes.get((package_family, subpackage_name))
        if target_node:
            lines.append(f"{type_aliases[source]} ..> {target_node.alias}")

    lines.append("")
    lines.append("@enduml")
    lines.append("")

    output_file = PLANTUML_DIR / PACKAGE_OUTPUT_TEMPLATE.format(package_name=top_level_package)
    output_file.write_text("\n".join(lines), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate PlantUML class diagrams.")
    parser.add_argument(
        "--render",
        action="store_true",
        help="Also render generated .puml files to .png using a local PlantUML jar.",
    )
    parser.add_argument(
        "--plantuml-jar",
        type=Path,
        default=None,
        help="Path to plantuml.jar. If omitted, the script looks for PLANTUML_JAR or ./plantuml.jar.",
    )
    return parser.parse_args()


def resolve_plantuml_jar(explicit_path: Path | None) -> Path | None:
    if explicit_path:
        return explicit_path.resolve()

    env_value = os.environ.get("PLANTUML_JAR")
    if env_value:
        return Path(env_value).resolve()

    local_default = REPO_ROOT / "plantuml.jar"
    if local_default.exists():
        return local_default

    return None


def render_pngs(plantuml_jar: Path) -> None:
    if not plantuml_jar.exists():
        raise FileNotFoundError(f"PlantUML jar not found: {plantuml_jar}")

    PNG_DIR.mkdir(parents=True, exist_ok=True)

    for puml_file in sorted(PLANTUML_DIR.glob("*.puml")):
        subprocess.run(
            ["java", "-jar", str(plantuml_jar), "-tpng", str(puml_file)],
            check=True,
        )
        generated_png = puml_file.with_suffix(".png")
        target_png = PNG_DIR / generated_png.name
        if generated_png.exists():
            generated_png.replace(target_png)
            print(f"Rendered {target_png}")


def main() -> None:
    args = parse_args()
    (
        types_by_full_name,
        extends_relations,
        implements_relations,
        usage_relations,
        families,
        package_nodes,
    ) = build_model()

    PLANTUML_DIR.mkdir(parents=True, exist_ok=True)
    PNG_DIR.mkdir(parents=True, exist_ok=True)

    for old_file in PLANTUML_DIR.glob("class-diagram-*.puml"):
        old_file.unlink()

    write_summary_diagram(families, package_nodes, usage_relations, types_by_full_name)

    for top_level_package in sorted(families):
        write_package_diagram(
            top_level_package,
            families,
            package_nodes,
            types_by_full_name,
            extends_relations,
            implements_relations,
            usage_relations,
        )

    print(f"Generated {SUMMARY_OUTPUT_FILE}")
    for top_level_package in sorted(families):
        print(f"Generated {PLANTUML_DIR / PACKAGE_OUTPUT_TEMPLATE.format(package_name=top_level_package)}")
    print(f"PNG output directory ready: {PNG_DIR}")

    if args.render:
        plantuml_jar = resolve_plantuml_jar(args.plantuml_jar)
        if plantuml_jar is None:
            raise FileNotFoundError(
                "No PlantUML jar found. Pass --plantuml-jar, set PLANTUML_JAR, or place plantuml.jar in the repo root."
            )
        render_pngs(plantuml_jar)


if __name__ == "__main__":
    main()
