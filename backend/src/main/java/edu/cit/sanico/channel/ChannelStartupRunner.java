package edu.cit.sanico.channel;

import edu.cit.sanico.inventory.InventoryItem;
import edu.cit.sanico.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
class ChannelStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChannelStartupRunner.class);

    private static final Map<String, String> PRODUCT_TO_SKU = Map.of(
            "P100", "JLN-2606",
            "P200", "JLN-6772",
            "P300", "JLN-7018"
    );

    private final TianggeChannelService tianggeChannelService;
    private final InventoryService inventoryService;
    private final edu.cit.sanico.inventory.InventoryRepository inventoryRepository;

    ChannelStartupRunner(TianggeChannelService tianggeChannelService,
                         InventoryService inventoryService,
                         edu.cit.sanico.inventory.InventoryRepository inventoryRepository) {
        this.tianggeChannelService = tianggeChannelService;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[CHANNEL] Executing startup tasks: Sending initial heartbeat...");
        tianggeChannelService.sendHeartbeat();

        log.info("[CHANNEL] Checking inventory items...");
        List<InventoryItem> items = inventoryService.getAllItems();
        if (items.isEmpty()) {
            log.info("[CHANNEL] Inventory is empty. Seeding initial products (P100, P200, P300)...");
            inventoryRepository.save(new InventoryItem("P100", "Wireless Mouse", 25));
            inventoryRepository.save(new InventoryItem("P200", "Mechanical Keyboard", 10));
            inventoryRepository.save(new InventoryItem("P300", "USB-C Hub", 15));
            items = inventoryService.getAllItems();
        } else {
            for (InventoryItem item : items) {
                if (item.getStock() <= 0) {
                    int defaultStock = "P100".equals(item.getProductId()) ? 25 : ("P200".equals(item.getProductId()) ? 10 : 15);
                    log.info("[CHANNEL] Item {} has {} stock on startup. Reseeding to {} units...", item.getProductId(), item.getStock(), defaultStock);
                    item.setStock(defaultStock);
                    inventoryRepository.save(item);
                }
            }
            items = inventoryService.getAllItems();
        }

        java.util.List<TianggeChannelService.ListingItem> listings = new java.util.ArrayList<>();
        java.util.List<TianggeChannelService.StockItem> stocks = new java.util.ArrayList<>();

        for (InventoryItem item : items) {
            String supplierSku = PRODUCT_TO_SKU.get(item.getProductId());
            if (supplierSku != null) {
                listings.add(new TianggeChannelService.ListingItem(item.getProductId(), item.getName(), supplierSku));
                stocks.add(new TianggeChannelService.StockItem(item.getProductId(), item.getStock()));
            }
        }

        if (!listings.isEmpty()) {
            log.info("[CHANNEL] Publishing {} listings to Tiangge in one request...", listings.size());
            tianggeChannelService.publishListings(listings);
        }

        if (!stocks.isEmpty()) {
            log.info("[CHANNEL] Updating stock for {} items on Tiangge...", stocks.size());
            tianggeChannelService.updateStocks(stocks);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        log.debug("[CHANNEL] Sending 30s heartbeat to Tiangge and LegacySupply...");
        tianggeChannelService.sendHeartbeat();
    }
}
