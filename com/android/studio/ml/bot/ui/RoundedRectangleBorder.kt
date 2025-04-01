// ... 已有代码 ...

override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2d = g.create() as Graphics2D
    try {
        // ... 已有设置代码 ...
        
        // 修改颜色获取方式，添加异常处理
        val borderColor = try {
            JBColor.border
        } catch (e: AssertionError) {
            Color(0x888888) // 使用灰色作为回退颜色
        }
        
        g2d.color = borderColor
        // ... 剩余绘制逻辑 ...
    } finally {
        g2d.dispose()
    }
}