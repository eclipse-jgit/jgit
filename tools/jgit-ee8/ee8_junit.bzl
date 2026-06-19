load("@rules_java//java:defs.bzl", "java_test")

_OUTPUT = """import org.junit.runners.Suite;
import org.junit.runner.RunWith;

@RunWith(Suite.class)
@Suite.SuiteClasses({%s})
@SuppressWarnings("DefaultPackage")
public class %s {}
"""

_PREFIXES = ("org", "com", "edu")

def _safe_index(items, value):
    for i, item in enumerate(items):
        if value == item:
            return i
    return -1

def _as_class_name(src):
    fname = src.path
    toks = fname[:-5].split("/")
    findex = -1
    for prefix in _PREFIXES:
        findex = _safe_index(toks, prefix)
        if findex != -1:
            break
    if findex == -1:
        fail("%s does not contain any of %s" % (fname, _PREFIXES))
    return ".".join(toks[findex:]) + ".class"

def _suite_impl(ctx):
    classes = []
    for src in ctx.attr.suite_srcs:
        for file in src.files.to_list():
            classes.append(_as_class_name(file))
    ctx.actions.write(
        output = ctx.outputs.out,
        content = _OUTPUT % (",".join(classes), ctx.attr.outname),
    )

_ee8_junit_suite = rule(
    attrs = {
        "suite_srcs": attr.label_list(allow_files = [".java"]),
        "outname": attr.string(),
    },
    outputs = {"out": "%{name}.java"},
    implementation = _suite_impl,
)

def ee8_junit_tests(name, suite_srcs, transformed_srcs, **kwargs):
    suite_name = name.replace("-", "_") + "TestSuite"
    _ee8_junit_suite(
        name = suite_name,
        suite_srcs = suite_srcs,
        outname = suite_name,
        testonly = 1,
    )
    java_test(
        name = name,
        test_class = suite_name,
        srcs = transformed_srcs + [":" + suite_name],
        **kwargs
    )
