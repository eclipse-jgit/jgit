diff --git a/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefDatabase.java b/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefDatabase.java
index 6784b49..593aaac 100644
--- a/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefDatabase.java
+++ b/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefDatabase.java
@@ -56,6 +56,7 @@
 import org.eclipse.jgit.lib.Ref;
 import org.eclipse.jgit.lib.RefDatabase;
 import org.eclipse.jgit.lib.RefRename;
+import org.eclipse.jgit.lib.RefUpdate;
 import org.eclipse.jgit.lib.SymbolicRef;
 import org.eclipse.jgit.revwalk.RevObject;
 import org.eclipse.jgit.revwalk.RevTag;
@@ -211,7 +212,7 @@ private static Ref recreate(Ref old, Ref leaf) {
 	}
 
 	@Override
-	public DfsRefUpdate newUpdate(String refName, boolean detach)
+	public RefUpdate newUpdate(String refName, boolean detach)
 			throws IOException {
 		boolean detachingSymbolicRef = false;
 		Ref ref = getOneRef(refName);
@@ -233,8 +234,8 @@ public DfsRefUpdate newUpdate(String refName, boolean detach)
 	@Override
 	public RefRename newRename(String fromName, String toName)
 			throws IOException {
-		DfsRefUpdate src = newUpdate(fromName, true);
-		DfsRefUpdate dst = newUpdate(toName, true);
+		RefUpdate src = newUpdate(fromName, true);
+		RefUpdate dst = newUpdate(toName, true);
 		return new DfsRefRename(src, dst);
 	}
 
diff --git a/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefRename.java b/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefRename.java
index a4cb791..d9c2bc7 100644
--- a/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefRename.java
+++ b/org.eclipse.jgit/src/org/eclipse/jgit/internal/storage/dfs/DfsRefRename.java
@@ -47,10 +47,11 @@
 
 import org.eclipse.jgit.lib.ObjectId;
 import org.eclipse.jgit.lib.RefRename;
+import org.eclipse.jgit.lib.RefUpdate;
 import org.eclipse.jgit.lib.RefUpdate.Result;
 
 final class DfsRefRename extends RefRename {
-	DfsRefRename(DfsRefUpdate src, DfsRefUpdate dst) {
+	DfsRefRename(RefUpdate src, RefUpdate dst) {
 		super(src, dst);
 	}
 
