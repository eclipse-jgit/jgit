package org.eclipse.jgit.treewalk.filter;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class OrTreeFilterTest {
    @Test
    public void testGetPathsBestEffort_Binary() {
        PathFilter p1 = PathFilter.create("p1");
        PathFilter p2 = PathFilter.create("p2");

        TreeFilter treeFilter = OrTreeFilter.create(p1, p2);
        Optional<Set<byte[]>> resultPaths = treeFilter.getPathsBestEffort();
        Assert.assertTrue(resultPaths.isPresent());

        Set<String> resultPathStrs = resultPaths.get()
                .stream()
                .map(String::new)
                .collect(Collectors.toSet());

        Assert.assertEquals(resultPathStrs.size(), 2);
        Assert.assertTrue(resultPathStrs.contains(p1.getPath()));
        Assert.assertTrue(resultPathStrs.contains(p2.getPath()));
    }

    @Test
    public void testGetPathsBestEffort_List() {
        List<String> paths = List.of("p1", "p2", "p3");
        List<TreeFilter> pathFilters = paths.stream()
                .map(PathFilter::create)
                .collect(Collectors.toList());

        TreeFilter treeFilter = OrTreeFilter.create(pathFilters);
        Optional<Set<byte[]>> resultPaths = treeFilter.getPathsBestEffort();
        Assert.assertTrue(resultPaths.isPresent());

        Set<String> resultPathStrs = resultPaths.get()
                .stream()
                .map(String::new)
                .collect(Collectors.toSet());

        Assert.assertEquals(resultPathStrs.size(), 3);
        for (String path : paths) {
            Assert.assertTrue(resultPathStrs.contains(path));
        }
    }

    @Test
    public void testGetPathsBestEffort_None() {
        TreeFilter treeFilter = OrTreeFilter.create(TreeFilter.ANY_DIFF, TreeFilter.ANY_DIFF);
        Optional<Set<byte[]>> result = treeFilter.getPathsBestEffort();
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetPathsBestEffort_One() {
        final String path = "p1";
        TreeFilter treeFilter = OrTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF);
        Optional<Set<byte[]>> resultPaths = treeFilter.getPathsBestEffort();
        Assert.assertTrue(resultPaths.isPresent());

        Set<String> resultPathStrs = resultPaths.get()
                .stream()
                .map(String::new)
                .collect(Collectors.toSet());
        Assert.assertEquals(resultPathStrs.size(), 1);
        Assert.assertTrue(resultPathStrs.contains(path));
    }
}
