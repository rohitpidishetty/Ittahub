package com.ittahub.ITTaHub.Utility;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

class Tree {

    protected String node;
    protected String type;
    protected String path;
    protected List<Tree> children = new ArrayList<>();

    public Tree() {
    }

    public Tree(String node, String type) {
        this.node = node;
        this.type = type;
    }
}

@Component
public class FileStructuring {

    public Tree root = null;

    public void buildTree(String file) {

        String[] path = file.split("/");
        Tree travel = root;
        for (String p : path) {
            if (root == null) {
                root = new Tree();
                travel = root;
                travel.node = p;
                travel.path = file;
                travel.type = Pattern.matches(".+\\.[a-zA-Z0-9]+$", p)
                        ? "file"
                        : "folder";
            } else {
                if (!p.equals(travel.node)) {
                    // Check if path already has p
                    Tree reuse = null;
                    for (Tree ex : travel.children) {
                        if (ex.node.equals(p)) {
                            reuse = ex;
                            break;
                        }
                    }
                    if (reuse != null) travel = reuse;
                    else {
                        if (Pattern.matches(".+\\.[a-zA-Z0-9]+$", p)) {
                            Tree leaf = new Tree();
                            leaf.node = p;
                            leaf.type = "file";
                            leaf.children = null;
                            leaf.path = file;
                            travel.children.add(leaf);
                        } else {
                            Tree newChild = new Tree(p, "folder");
                            newChild.path = file;
                            travel.children.add(newChild);
                            travel = newChild;
                        }
                    }
                }
            }
        }

    }

    public Map<String, Object> treeToJson(Tree node) {
        Map<String, Object> json = new HashMap<>();
        json.put("node", node.node);
        json.put("type", node.type);
        json.put("path", node.path);
        if (node.children != null && !node.children.isEmpty()) {
            List<Map<String, Object>> childrenList = new ArrayList<>();
            for (Tree child : node.children) childrenList.add(treeToJson(child));
            json.put("children", childrenList);
        }
        return json;
    }

}
