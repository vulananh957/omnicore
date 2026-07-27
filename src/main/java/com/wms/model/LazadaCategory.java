package com.wms.model;

/**
 * LazadaCategory — one node of Lazada's /category/tree/get response, mirrored
 * into the local DB so the product wizard can pick a valid leaf category.
 */
public class LazadaCategory {
    private long lazadaCategoryId;
    private Long parentId;
    private String name;
    private boolean leaf;
    private boolean hasVariation;
    private int depth;
    private String path;

    public long getLazadaCategoryId() { return lazadaCategoryId; }
    public void setLazadaCategoryId(long v) { this.lazadaCategoryId = v; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long v) { this.parentId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public boolean isLeaf() { return leaf; }
    public void setLeaf(boolean v) { this.leaf = v; }

    public boolean isHasVariation() { return hasVariation; }
    public void setHasVariation(boolean v) { this.hasVariation = v; }

    public int getDepth() { return depth; }
    public void setDepth(int v) { this.depth = v; }

    public String getPath() { return path; }
    public void setPath(String v) { this.path = v; }
}