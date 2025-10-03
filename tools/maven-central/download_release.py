#!/usr/bin/env python3
import argparse
import os
import pathlib
import requests
import subprocess
import xml.etree.ElementTree as ET
import yaml
from concurrent.futures import ThreadPoolExecutor
from functools import partial


BASE_REPO_URL = "https://repo.eclipse.org/content/groups/releases"
JGIT_GROUP_ID = "org.eclipse.jgit"
JGIT_PARENT_ARTIFACT_ID = "org.eclipse.jgit-parent"
DOWNLOAD_DIR = "staging-deploy"


class Dumper(yaml.Dumper):
    def increase_indent(self, flow=False, *args, **kwargs):
        return super().increase_indent(flow=flow, indentless=False)


def fetch_and_parse_pom(url):
    """Fetches and parses a Maven POM file from a URL."""
    print(f"Fetching parent POM from {url}")
    try:
        response = requests.get(url)
        response.raise_for_status()
        return ET.fromstring(response.content)
    except requests.exceptions.RequestException as e:
        print(f"Error fetching POM: {e}")
        exit(1)


def get_pom_info(pom_root):
    """Extracts GAV and modules from a parsed POM XML."""
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}

    group_id_elem = pom_root.find("m:groupId", ns)
    if group_id_elem is None:
        group_id_elem = pom_root.find("m:parent/m:groupId", ns)

    version_elem = pom_root.find("m:version", ns)
    if version_elem is None:
        version_elem = pom_root.find("m:parent/m:version", ns)

    java_version_elem = None
    for prop in pom_root.findall("m:properties", ns):
        java_version_elem = prop.find("m:java.version", ns)
        if java_version_elem is not None:
            break

    info = {
        "groupId": group_id_elem.text,
        "artifactId": pom_root.find("m:artifactId", ns).text,
        "version": version_elem.text,
        "java_version": java_version_elem.text,
        "modules": [
            module.text for module in pom_root.findall("m:modules/m:module", ns)
        ],
    }
    return info


def download_artifacts(artifacts_to_download):
    """Downloads all artifacts for the given GAVs into the DOWNLOAD_DIR."""
    for artifact in artifacts_to_download:
        group_path = artifact["groupId"].replace(".", "/")
        base_url = f"{BASE_REPO_URL}/{group_path}/{artifact['artifactId']}/{artifact['version']}"
        group_download_dir = f"{DOWNLOAD_DIR}/{group_path}"
        artifact_download_dir = (
            f"{group_download_dir}/{artifact['artifactId']}/{artifact['version']}"
        )
        if not os.path.exists(artifact_download_dir):
            os.makedirs(artifact_download_dir)

        filenames = [f"{artifact['artifactId']}-{artifact['version']}.pom"]
        if "parent" not in artifact["artifactId"]:
            filenames.extend(
                [
                    f"{artifact['artifactId']}-{artifact['version']}.jar",
                    f"{artifact['artifactId']}-{artifact['version']}-sources.jar",
                    f"{artifact['artifactId']}-{artifact['version']}-javadoc.jar",
                    f"{artifact['artifactId']}-{artifact['version']}-cyclonedx.json",
                    f"{artifact['artifactId']}-{artifact['version']}.sh",
                ]
            )

        print(
            f"\nDownloading artifacts for {artifact['groupId']}:{artifact['artifactId']}:{artifact['version']}"
        )
        download_task = partial(download_file, artifact_download_dir, base_url)
        with ThreadPoolExecutor(max_workers=8) as executor:
            executor.map(download_task, filenames)


def download_file(artifact_download_dir, base_url, filename):
    url = f"{base_url}/{filename}"
    local_path = os.path.join(artifact_download_dir, filename)

    try:
        with requests.get(url, stream=True) as r:
            if r.status_code == 200:
                with open(local_path, "wb") as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                print(f"  Downloaded: {filename}")
            elif r.status_code == 404:
                pass
            else:
                print(
                    f"  Warning: Failed to download {filename} (Status: {r.status_code})"
                )
    except requests.exceptions.RequestException as e:
        print(f"  Error downloading {filename}: {e}")


def sign_artifacts(passphrase_file):
    artifact_dir = pathlib.Path(DOWNLOAD_DIR)
    print("Signing artifacts")
    for file in artifact_dir.rglob("*"):
        if file.is_file() and file.suffix in {".jar", ".pom", ".sh", ".json"}:
            try:
                subprocess.run(
                    [
                        "gpg",
                        "--batch",
                        "--yes",
                        "--pinentry-mode",
                        "loopback",
                        "--passphrase-file",
                        passphrase_file,
                        "--armor",
                        "--detach-sign",
                        str(file),
                    ],
                    check=True,
                )
                print(f"  Signed: {str(file)}")
            except subprocess.CalledProcessError as e:
                print(f"Error signing {file}: {e.stderr}")


def jreleaser_set_values(jgit_version, java_version):
    if not jgit_version:
        print("jgit_version undefined")
    if not java_version:
        print("java_version undefined")

    with open("jreleaser.yml.template", "r") as f:
        data = yaml.safe_load(f)

    data["project"]["version"] = jgit_version
    data["project"]["languages"]["java"]["version"] = java_version

    with open("jreleaser.yml", "w") as f:
        yaml.dump(data, f, sort_keys=False, Dumper=Dumper)


def main():
    """Main execution function."""
    parser = argparse.ArgumentParser(
        description="""Download artifacts of a JGit release from repo.eclipse.org.""",
        usage="%(prog)s <version> [--passphrase_file <path to passphrase file> (default: ~/.gnupg/passphrase)]",
    )
    # 'version' as a positional argument
    parser.add_argument(
        "version", help="The JGit version to process (e.g., 5.13.4.202507202350-r)."
    )
    parser.add_argument(
        "--passphrase_file",
        help="Path to file containing gpg passphrase",
        default=f"{pathlib.Path.home()}/.gnupg/passphrase",
    )
    args = parser.parse_args()
    jgit_version = args.version
    passphrase_file = args.passphrase_file

    group_path = JGIT_GROUP_ID.replace(".", "/")
    filename = f"{JGIT_PARENT_ARTIFACT_ID}-{jgit_version}.pom"
    parent_pom_url = f"{BASE_REPO_URL}/{group_path}/{JGIT_PARENT_ARTIFACT_ID}/{jgit_version}/{filename}"

    pom_root = fetch_and_parse_pom(parent_pom_url)
    parent_info = get_pom_info(pom_root)

    if parent_info["version"] != jgit_version:
        print(
            f"Warning: The version in the POM ('{parent_info['version']}') does not match the requested version ('{jgit_version}')."
        )
        print("Proceeding with the version found in the POM file.")

    print("\n--- Project Information ---")
    print(f"GroupId:    {parent_info['groupId']}")
    print(f"ArtifactId: {parent_info['artifactId']}")
    print(f"Version:    {parent_info['version']}")
    print(f"Found {len(parent_info['modules'])} modules.")

    artifacts_to_process = []
    artifacts_to_process.append(
        {
            "groupId": parent_info["groupId"],
            "artifactId": parent_info["artifactId"],
            "version": parent_info["version"],
        }
    )
    for module in parent_info["modules"]:
        if not module.endswith((".test", ".coverage", ".benchmarks")):
            artifacts_to_process.append(
                {
                    "groupId": parent_info["groupId"],
                    "artifactId": module,
                    "version": parent_info["version"],
                }
            )

    download_artifacts(artifacts_to_process)
    sign_artifacts(passphrase_file)
    jreleaser_set_values(parent_info["version"], parent_info["java_version"])


if __name__ == "__main__":
    main()
