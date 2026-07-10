<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<style>
.config-card {
    background: #fff;
    border: 1px solid var(--border, #e5eaf3);
    border-radius: 12px;
    padding: 28px 32px;
    max-width: 680px;
    margin: 0 auto;
}
.config-card h2 {
    margin: 0 0 6px;
    font-size: 18px;
    color: var(--navy, #10375c);
    font-weight: 700;
}
.config-card .desc {
    font-size: 13px;
    color: rgba(16, 55, 92, 0.55);
    margin-bottom: 28px;
    line-height: 1.5;
}
.config-form-group {
    margin-bottom: 20px;
}
.config-form-group label {
    display: block;
    font-size: 13px;
    font-weight: 700;
    color: var(--navy, #10375c);
    margin-bottom: 6px;
}
.config-form-group .hint {
    font-size: 11px;
    color: rgba(16, 55, 92, 0.45);
    margin-top: 3px;
}
.config-input {
    width: 100%;
    max-width: 200px;
    padding: 8px 12px;
    border: 1.5px solid var(--border, #e5eaf3);
    border-radius: 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--navy, #10375c);
    background: #fff;
    text-align: right;
}
.config-input:focus {
    outline: none;
    border-color: #1d4ed8;
}
.config-saved-val {
    font-size: 12px;
    color: rgba(16, 55, 92, 0.5);
    margin-top: 4px;
}
.config-actions {
    margin-top: 28px;
    display: flex;
    gap: 12px;
    align-items: center;
}
.btn-save-config {
    background: #1d4ed8;
    color: #fff;
    border: none;
    border-radius: 8px;
    padding: 10px 24px;
    font-size: 14px;
    font-weight: 700;
    cursor: pointer;
    transition: background 0.15s;
}
.btn-save-config:hover { background: #1e40af; }
.btn-cancel-config {
    background: #fff;
    color: var(--navy, #10375c);
    border: 1.5px solid var(--border, #e5eaf3);
    border-radius: 8px;
    padding: 10px 20px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
}
.btn-cancel-config:hover { background: #f1f5f9; }

.warning-note {
    margin-top: 20px;
    padding: 12px 16px;
    background: #fffbeb;
    border: 1px solid #fde68a;
    border-radius: 8px;
    font-size: 12px;
    color: #92400e;
    line-height: 1.5;
}
.warning-note strong { font-weight: 700; }
</style>

<div class="config-card">
    <h2>Cấu hình ngưỡng cảnh báo giá bán</h2>
    <p class="desc">
        Khi nhân viên Sales đặt giá bán trên kênh thấp hơn ngưỡng, hệ thống sẽ hiển thị chip cảnh báo màu.
        Ngưỡng này chỉ mang tính cảnh báo — nhân viên vẫn có thể lưu giá thấp hơn.
    </p>

    <form method="POST" action="${pageContext.request.contextPath}/business/config" id="configForm">
        <div class="config-form-group">
            <label for="marginLow">Ngưỡng "Lãi ít" (vàng)</label>
            <input class="config-input" type="number" step="0.01" min="0" max="1"
                   id="marginLow" name="marginLow"
                   value="${thresholds['pricing.warn_margin_low'] != null ? thresholds['pricing.warn_margin_low'] : '0.10'}"/>
            <div class="hint">Margin thấp hơn ngưỡng này → chip vàng "Lãi ít". Nhập dạng thập phân, ví dụ: 0.10 = 10%</div>
        </div>

        <div class="config-form-group">
            <label for="marginBreakeven">Ngưỡng "Hoà vốn" (cam)</label>
            <input class="config-input" type="number" step="0.01" min="-1" max="1"
                   id="marginBreakeven" name="marginBreakeven"
                   value="${thresholds['pricing.warn_margin_breakeven'] != null ? thresholds['pricing.warn_margin_breakeven'] : '0.00'}"/>
            <div class="hint">Margin thấp hơn ngưỡng này → chip cam "Hoà vốn". Ví dụ: 0.00 = 0%</div>
        </div>

        <div class="config-form-group">
            <label for="marginLoss">Ngưỡng "Bán lỗ" (đỏ)</label>
            <input class="config-input" type="number" step="0.01" min="-1" max="0"
                   id="marginLoss" name="marginLoss"
                   value="${thresholds['pricing.warn_margin_loss_threshold'] != null ? thresholds['pricing.warn_margin_loss_threshold'] : '-0.05'}"/>
            <div class="hint">Margin thấp hơn ngưỡng này → chip đỏ "Bán lỗ". Ví dụ: -0.05 = -5%</div>
        </div>

        <div class="warning-note">
            <strong>Lưu ý:</strong> Các ngưỡng được tính theo công thức
            <em>margin = (giá bán − giá vốn) / giá vốn</em>.
            Ngưỡng phải thỏa mãn: <strong>Lãi ít &gt; Hoà vốn &gt; Bán lỗ</strong>
            (ví dụ: 0.10 &gt; 0.00 &gt; -0.05).
        </div>

        <div class="config-actions">
            <button type="submit" class="btn-save-config">Lưu cấu hình</button>
            <a href="${pageContext.request.contextPath}/business/dashboard" class="btn-cancel-config">Hủy</a>
        </div>
    </form>
</div>
