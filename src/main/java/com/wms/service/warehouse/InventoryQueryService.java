package com.wms.service.warehouse;

import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.model.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InventoryQueryService — Service providing ATP (Available-To-Promise)
 * per sales channel.
 *
 * <p>Why it exists: previously ATP only returned the raw qty_available of
 * the warehouse without subtracting each channel's buffer_stock. As a
 * result, a SKU with 10 units in the warehouse was visible to Shopee,
 * Lazada, and TikTok simultaneously, and orders from all three could be
 * synced in — leading to overselling.</p>
 *
 * <p>Formula:</p>
 * <pre>
 *   ATP(channel) = max(0, qty_available - buffer_stock[channel])
 * </pre>
 *
 * <p>Used by Sales staff before approving an order, and by the Lazada
 * sync scheduler to reject orders that exceed the buffer.</p>
 */
public class InventoryQueryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryQueryService.class);

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();

    /**
     * Tính ATP cho 1 SKU ở 1 kho, có trừ buffer của channel cụ thể.
     *
     * @param productId    Sản phẩm cần check
     * @param warehouseId  Kho
     * @param channelId    Channel (để lấy buffer_stock)
     * @return Số lượng có thể hứa bán cho kênh này (đã trừ buffer)
     */
    public int getAvailableForChannel(int productId, int warehouseId, int channelId) {
        int rawAvailable = inventoryDAO.getAvailableStock(productId, warehouseId);
        Channel ch = channelDAO.findById(channelId);
        double buffer = (ch != null) ? ch.getBufferStock() : 0d;
        int adjusted = (int) Math.max(0d, rawAvailable - buffer);
        log.debug("getAvailableForChannel: productId={} warehouseId={} channelId={} raw={} buffer={} atp={}",
            productId, warehouseId, channelId, rawAvailable, buffer, adjusted);
        return adjusted;
    }

    /**
     * Tính tổng ATP trên tất cả các kho cho 1 SKU + 1 channel.
     * Hữu ích cho Sales nhìn thấy "tổng còn bán được bao nhiêu" ở mọi kho.
     */
    public int getTotalAvailableForChannel(int productId, int channelId) {
        int total = 0;
        for (com.wms.model.Warehouse w : new com.wms.dao.WarehouseDAO().findAll()) {
            total += getAvailableForChannel(productId, w.getWarehouseId(), channelId);
        }
        return total;
    }
}
