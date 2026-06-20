def _jakarta_to_javax_srcjar_impl(ctx):
    # Eclipse Transformer 1.0.0 selects archive handling from the input
    # extension. The normalized source archive therefore uses .jar.
    normalized_srcs = ctx.actions.declare_file(ctx.label.name + "-normalized.jar")
    output_srcjar = ctx.actions.declare_file(ctx.label.name + ".srcjar")

    zipper = ctx.executable._zipper
    transformer = ctx.executable._transformer
    src_prefix = ctx.attr.src_prefix

    args = ctx.actions.args()
    args.add("c")
    args.add(normalized_srcs)
    for src in ctx.files.sources:
        short_path = src.short_path
        if short_path.startswith(src_prefix):
            internal = short_path[len(src_prefix):]
        else:
            marker = "/" + src_prefix
            marker_index = short_path.find(marker)
            if marker_index < 0:
                fail("Source %s does not contain src_prefix %s" % (short_path, src_prefix))
            internal = short_path[marker_index + len(marker):]
        args.add(internal + "=" + src.path)

    ctx.actions.run(
        inputs = ctx.files.sources,
        outputs = [normalized_srcs],
        executable = zipper,
        arguments = [args],
        mnemonic = "NormalizeJGitSrcJar",
        progress_message = "Normalizing JGit sources for %s" % ctx.label,
    )

    transform_args = ctx.actions.args()
    transform_args.add(normalized_srcs)
    transform_args.add(output_srcjar)
    transform_args.add("-tr")
    transform_args.add(ctx.file.renames)

    ctx.actions.run(
        inputs = [normalized_srcs, ctx.file.renames],
        outputs = [output_srcjar],
        executable = transformer,
        arguments = [transform_args],
        mnemonic = "TransformJakartaToJavax",
        progress_message = "Rewriting jakarta.servlet imports for %s" % ctx.label,
    )

    return [DefaultInfo(files = depset([output_srcjar]))]

jakarta_to_javax_srcjar = rule(
    implementation = _jakarta_to_javax_srcjar_impl,
    attrs = {
        "renames": attr.label(
            allow_single_file = True,
            mandatory = True,
        ),
        "sources": attr.label_list(
            allow_files = [".java"],
            mandatory = True,
        ),
        "src_prefix": attr.string(mandatory = True),
        "_transformer": attr.label(
            default = Label("//tools/jgit-ee8:transformer"),
            executable = True,
            cfg = "exec",
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
            cfg = "exec",
        ),
    },
)
