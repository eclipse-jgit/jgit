sh_test(
    name = "generated_srcs_test",
    srcs = ["generated_srcs_test.sh"],
    args = [
        "--module",
        "org.eclipse.jgit.http.server/src/",
        "$(location //org.eclipse.jgit.http.server.ee8:jgit-http-server-ee8-srcs)",
        "$(locations //org.eclipse.jgit.http.server:srcs)",
        "--module",
        "org.eclipse.jgit.lfs.server/src/",
        "$(location //org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8-srcs)",
        "$(locations //org.eclipse.jgit.lfs.server:srcs)",
    ],
    data = [
        "//org.eclipse.jgit.http.server.ee8:jgit-http-server-ee8-srcs",
        "//org.eclipse.jgit.http.server:srcs",
        "//org.eclipse.jgit.lfs.server.ee8:jgit-lfs-server-ee8-srcs",
        "//org.eclipse.jgit.lfs.server:srcs",
    ],
)
