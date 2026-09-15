package com.poodicraft.shopkeeper.game;

import java.util.ArrayList;

/**
 * Coarse walkability grid over the shop floor with an A* search.
 *
 * <p>Shelves and fixtures mark cells blocked; customers and staff path around them.
 * Paths are smoothed with a line-of-sight pass so characters cut corners instead of
 * stepping along the grid.
 */
public final class NavGrid {
    public final float originX, originZ;
    public final float cellSize;
    public final int cols, rows;

    private final boolean[] blocked;

    // A* working set, reused between searches to keep the simulation allocation-free.
    private final float[] gScore;
    private final float[] fScore;
    private final int[] cameFrom;
    private final boolean[] closed;
    private final int[] heap;
    private final int[] heapIndex;
    private int heapSize;

    private static final int[] NEIGHBOR_DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] NEIGHBOR_DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    public NavGrid(float originX, float originZ, float width, float depth, float cellSize) {
        this.originX = originX;
        this.originZ = originZ;
        this.cellSize = cellSize;
        this.cols = Math.max(1, (int) Math.ceil(width / cellSize));
        this.rows = Math.max(1, (int) Math.ceil(depth / cellSize));
        int n = cols * rows;
        blocked = new boolean[n];
        gScore = new float[n];
        fScore = new float[n];
        cameFrom = new int[n];
        closed = new boolean[n];
        heap = new int[n + 1];
        heapIndex = new int[n];
    }

    public void clearObstacles() {
        for (int i = 0; i < blocked.length; i++) blocked[i] = false;
    }

    public int colOf(float worldX) { return (int) Math.floor((worldX - originX) / cellSize); }

    public int rowOf(float worldZ) { return (int) Math.floor((worldZ - originZ) / cellSize); }

    public float cellCenterX(int col) { return originX + (col + 0.5f) * cellSize; }

    public float cellCenterZ(int row) { return originZ + (row + 0.5f) * cellSize; }

    public boolean inBounds(int col, int row) {
        return col >= 0 && row >= 0 && col < cols && row < rows;
    }

    public boolean isBlocked(int col, int row) {
        if (!inBounds(col, row)) return true;
        return blocked[row * cols + col];
    }

    public boolean isWalkableWorld(float x, float z) {
        return !isBlocked(colOf(x), rowOf(z));
    }

    /** Blocks every cell whose centre falls inside the rectangle, grown by {@code pad}. */
    public void blockRect(float minX, float minZ, float maxX, float maxZ, float pad) {
        int c0 = colOf(minX - pad), c1 = colOf(maxX + pad);
        int r0 = rowOf(minZ - pad), r1 = rowOf(maxZ + pad);
        for (int r = Math.max(0, r0); r <= Math.min(rows - 1, r1); r++) {
            for (int c = Math.max(0, c0); c <= Math.min(cols - 1, c1); c++) {
                blocked[r * cols + c] = true;
            }
        }
    }

    /** Finds the closest walkable cell to a point, for targets that sit inside geometry. */
    public boolean nearestWalkable(float x, float z, float[] out) {
        int c = colOf(x), r = rowOf(z);
        if (inBounds(c, r) && !blocked[r * cols + c]) {
            out[0] = x; out[1] = z;
            return true;
        }
        for (int radius = 1; radius < Math.max(cols, rows); radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int nc = c + dx, nr = r + dz;
                    if (!inBounds(nc, nr) || blocked[nr * cols + nc]) continue;
                    out[0] = cellCenterX(nc);
                    out[1] = cellCenterZ(nr);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A* between two world positions.
     *
     * @param out receives smoothed waypoints as x,z pairs; cleared first.
     * @return false when no route exists.
     */
    public boolean findPath(float startX, float startZ, float goalX, float goalZ, ArrayList<float[]> out) {
        out.clear();
        float[] fixed = new float[2];
        if (!nearestWalkable(startX, startZ, fixed)) return false;
        int start = index(colOf(fixed[0]), rowOf(fixed[1]));
        if (!nearestWalkable(goalX, goalZ, fixed)) return false;
        float targetX = goalX, targetZ = goalZ;
        int goal = index(colOf(fixed[0]), rowOf(fixed[1]));
        if (start < 0 || goal < 0) return false;

        if (start == goal) {
            out.add(new float[]{targetX, targetZ});
            return true;
        }

        for (int i = 0; i < gScore.length; i++) {
            gScore[i] = Float.MAX_VALUE;
            closed[i] = false;
            cameFrom[i] = -1;
            heapIndex[i] = -1;
        }
        heapSize = 0;
        gScore[start] = 0f;
        fScore[start] = heuristic(start, goal);
        heapPush(start);

        boolean found = false;
        while (heapSize > 0) {
            int current = heapPop();
            if (current == goal) { found = true; break; }
            closed[current] = true;
            int cc = current % cols, cr = current / cols;
            for (int i = 0; i < 8; i++) {
                int nc = cc + NEIGHBOR_DX[i], nr = cr + NEIGHBOR_DZ[i];
                if (!inBounds(nc, nr)) continue;
                int neighbor = nr * cols + nc;
                if (blocked[neighbor] || closed[neighbor]) continue;
                // Refuse diagonal moves that would clip a blocked corner.
                if (i >= 4 && (isBlocked(nc, cr) || isBlocked(cc, nr))) continue;
                float step = (i >= 4 ? 1.41421f : 1f) * cellSize;
                float tentative = gScore[current] + step;
                if (tentative >= gScore[neighbor]) continue;
                cameFrom[neighbor] = current;
                gScore[neighbor] = tentative;
                fScore[neighbor] = tentative + heuristic(neighbor, goal);
                if (heapIndex[neighbor] < 0) heapPush(neighbor);
                else siftUp(heapIndex[neighbor]);
            }
        }
        if (!found) return false;

        ArrayList<float[]> raw = new ArrayList<float[]>();
        int node = goal;
        while (node != -1) {
            raw.add(new float[]{cellCenterX(node % cols), cellCenterZ(node / cols)});
            node = cameFrom[node];
        }
        // Reverse into start-to-goal order.
        for (int i = raw.size() - 1; i >= 0; i--) out.add(raw.get(i));
        if (!out.isEmpty()) out.remove(0);
        out.add(new float[]{targetX, targetZ});
        smooth(out);
        return !out.isEmpty();
    }

    /** Drops waypoints that a straight line can skip. */
    private void smooth(ArrayList<float[]> path) {
        if (path.size() < 3) return;
        int i = 0;
        while (i < path.size() - 2) {
            float[] a = path.get(i);
            float[] c = path.get(i + 2);
            if (hasLineOfSight(a[0], a[1], c[0], c[1])) {
                path.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    public boolean hasLineOfSight(float x0, float z0, float x1, float z1) {
        float dx = x1 - x0, dz = z1 - z0;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(2, (int) (dist / (cellSize * 0.5f)));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            if (isBlocked(colOf(x0 + dx * t), rowOf(z0 + dz * t))) return false;
        }
        return true;
    }

    private int index(int col, int row) {
        return inBounds(col, row) ? row * cols + col : -1;
    }

    private float heuristic(int a, int b) {
        int ax = a % cols, az = a / cols;
        int bx = b % cols, bz = b / cols;
        int dx = Math.abs(ax - bx), dz = Math.abs(az - bz);
        // Octile distance: exact for 8-way movement, so A* stays admissible.
        return (Math.max(dx, dz) + 0.41421f * Math.min(dx, dz)) * cellSize;
    }

    // ------------------------------------------------------------- binary heap

    private void heapPush(int node) {
        heap[++heapSize] = node;
        heapIndex[node] = heapSize;
        siftUp(heapSize);
    }

    private int heapPop() {
        int top = heap[1];
        heapIndex[top] = -1;
        heap[1] = heap[heapSize--];
        if (heapSize > 0) {
            heapIndex[heap[1]] = 1;
            siftDown(1);
        }
        return top;
    }

    private void siftUp(int pos) {
        int node = heap[pos];
        while (pos > 1) {
            int parent = pos >> 1;
            if (fScore[heap[parent]] <= fScore[node]) break;
            heap[pos] = heap[parent];
            heapIndex[heap[pos]] = pos;
            pos = parent;
        }
        heap[pos] = node;
        heapIndex[node] = pos;
    }

    private void siftDown(int pos) {
        int node = heap[pos];
        while (true) {
            int child = pos << 1;
            if (child > heapSize) break;
            if (child + 1 <= heapSize && fScore[heap[child + 1]] < fScore[heap[child]]) child++;
            if (fScore[heap[child]] >= fScore[node]) break;
            heap[pos] = heap[child];
            heapIndex[heap[pos]] = pos;
            pos = child;
        }
        heap[pos] = node;
        heapIndex[node] = pos;
    }
}
