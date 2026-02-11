package com.ittahub.ITTaHub.Utility;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

enum ObjectType {
    File,
    Folder,
}

class FileTree {

    protected String parent;
    protected ObjectType type;
    protected String relativeFilePath;
    protected List<FileTree> children = new ArrayList<>();

    FileTree(String parent, ObjectType type, String relativeFilePath) {
        this.parent = parent;
        this.type = type;
        this.relativeFilePath = relativeFilePath;
    }
}

@Component
public class RootFilePathBuilder {

    private String[][] paths;

    public RootFilePathBuilder addPaths(String[] paths) {
        this.paths = new String[paths.length][];
        int index = 0;
        for (String path : paths) this.paths[index++] = path.split("/");
        return this;
    }

    FileTree root = null;

    public RootFilePathBuilder buildTree() {
        this.root = null;
        this.root = new FileTree(this.paths[0][0], ObjectType.Folder, this.paths[0][0]);
        for (String[] pathTokens : this.paths) {
            // DFS-path
            List<String> pathBuilder = new ArrayList<>(Arrays.asList(root.parent));
            FileTree extendPath = root;
            for (int i = 1; i < pathTokens.length; i++) {
                String blob = pathTokens[i];
                pathBuilder.add(blob);
                // Checking if the sub-file is already linked or not.
                FileTree existingPath = null;
                for (FileTree ft : extendPath.children) {
                    if (ft.parent.equals(blob)) {

                        existingPath = ft;
                        break;
                    }
                }
                if (existingPath != null) {
                    extendPath = existingPath;
                    continue;
                }
                FileTree child = new FileTree(
                        blob,
                        null,
                        String.join("/", pathBuilder)
                );

                int dotIndex = blob.lastIndexOf('.');
                boolean isFile = dotIndex > 0 && dotIndex < blob.length() - 1;

                child.type = isFile
                        ? ObjectType.File
                        : ObjectType.Folder;

                extendPath.children.add(child);
                extendPath = child;
            }
        }
        return this;
    }

    public Map<String, Object> treeToJson(FileTree node) {
        Map<String, Object> json = new HashMap<>();
        json.put("node", node.parent);
        json.put("type", node.type);
        json.put("path", "/" + node.relativeFilePath);
        if (node.children != null && !node.children.isEmpty()) {
            List<Map<String, Object>> childrenList = new ArrayList<>();
            for (FileTree child : node.children) childrenList.add(treeToJson(child));
            json.put("children", childrenList);
        }
        return json;
    }

    public Map<String, Object> normalize() {
        return treeToJson(this.root);
    }
}

