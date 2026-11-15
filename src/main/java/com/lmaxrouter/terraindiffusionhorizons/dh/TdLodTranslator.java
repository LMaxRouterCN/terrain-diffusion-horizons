package com.lmaxrouter.terraindiffusionhorizons.dh;

/**
 * DH generateLod 参数 -> TD 查询的坐标翻译层 (纯函数, 零状态零副作用).
 *
 * <p>所有 "DH 参数单位" 的假设集中在本文件; 运行时首调日志验证后若有出入,
 * 只改这里, 生成器主体不动. 依据: GOAL-PLAN "S2b 侦查案卷".</p>
 */
public final class TdLodTranslator {

    private TdLodTranslator() {
    }

    /**
     * API detail level dl 对应的 LOD 列宽 (块): 2^(dl+6).
     * dl0 = 64 块 (即 core 级 dl6); TDH 只供 dl0, 粗档交 DH DOWN_SAMPLED.
     */
    public static int columnWidthBlocks(byte apiDetailLevel) {
        return 1 << (apiDetailLevel + 6);
    }

    /**
     * 数据源覆盖的块矩形, 即 TD getRiverTerrainData 的 i/j 输入.
     *
     * <p>[实证 2026-11-15, GOAL-PLAN 数据正确性案] posX/posZ 单位 = 数据源边长(块) = 2^(dl+6),
     * dl0 即 64; 数据源恒 64x64 列 x 列宽 1 块, 边长与 widthInColumns 无关.
     * 旧假设 (pos = min 角块坐标) 被双会话首调比值恒 4 + DH 调用方字节码推翻, 弃.</p>
     *
     * @param widthInColumns pooledFullDataSource.getWidthInDataColumns() (dl0 恒 64, 留作 dl>0 扩展)
     * @return [i1, j1, i2, j2] = [Zmin, Xmin, Zmax, Xmax], 闭开区间 (TD: i=Z, j=X)
     */
    public static int[] dataSourceBlockBounds(int posX, int posZ, byte apiDetailLevel, int widthInColumns) {
        // pos 单位 = 数据源边长(块) = 2^(dl+6), dl0 即 64 [实证 2026-11-15]
        int size = columnWidthBlocks(apiDetailLevel);
        int bx = posX * size;
        int bz = posZ * size;
        return new int[]{bz, bx, bz + size, bx + size};
    }

    /**
     * TD 返回一维数组的线性索引 (行主序: idx = z * width + x, width 为 X 向).
     * TD 的行=Z / 列=X, 与 DH 列索引 (x=X, z=Z) 同轴序, 直接对齐.
     * TD 块级采样与 LOD 列 1:1 (dl0 时列宽=采样粒度), 无需插值.
     */
    public static int sampleIndex(int localColumnX, int localColumnZ, int arrayWidth) {
        return localColumnZ * arrayWidth + localColumnX;
    }
}
