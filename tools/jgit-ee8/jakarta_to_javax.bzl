def _jakarta_to_javax_srcjar_impl(ctx):
    output_srcjar = ctx.actions.declare_file(ctx.label.name + ".srcjar")
    src_prefix = ctx.attr.src_prefix

    args = ctx.actions.args()
    args.add(output_srcjar)
    args.add(src_prefix)
    args.add_all(ctx.files.sources)

    ctx.actions.run_shell(
        inputs = ctx.files.sources,
        outputs = [output_srcjar],
        arguments = [args],
        command = """
set -euo pipefail

out="$1"
src_prefix="$2"
shift 2
out="${PWD}/${out}"

tmp="$(mktemp -d "${TMPDIR:-/tmp}/jgit-ee8-srcs.XXXXXX")"
trap 'rm -rf "${tmp}"' EXIT

for src in "$@"; do
  case "${src}" in
    *"${src_prefix}"*)
      internal="${src#*"${src_prefix}"}"
      ;;
    *)
      echo "Source ${src} does not contain ${src_prefix}" >&2
      exit 1
      ;;
  esac
  mkdir -p "${tmp}/$(dirname "${internal}")"
  sed 's/jakarta\\.servlet/javax.servlet/g' "${src}" > "${tmp}/${internal}"
done

cd "${tmp}"
zip -qDr "${out}" .
""",
        mnemonic = "GenerateJGitEe8SrcJar",
        progress_message = "Rewriting servlet imports for %s" % ctx.label,
    )

    return [DefaultInfo(files = depset([output_srcjar]))]

jakarta_to_javax_srcjar = rule(
    implementation = _jakarta_to_javax_srcjar_impl,
    attrs = {
        "sources": attr.label_list(
            allow_files = [".java"],
            mandatory = True,
        ),
        "src_prefix": attr.string(mandatory = True),
    },
)
