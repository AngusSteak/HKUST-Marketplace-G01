package com.hkust.marketplace.controllers.seller;

import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bar handlers must reach bars the chart created before the handler was attached. */
class SellerStatsChartTest {

    @Test
    void aBarThatAlreadyExistsGetsTheAction() {
        XYChart.Data<Number, String> data = new XYChart.Data<>(5, "Charger");
        Region bar = new Region();
        data.setNode(bar);   // the chart creates the node as soon as the data is added
        List<Node> reached = new ArrayList<>();

        SellerDashboardController.whenBarShown(data, reached::add);

        assertEquals(List.of(bar), reached);
    }

    @Test
    void aBarCreatedLaterGetsTheActionToo() {
        XYChart.Data<Number, String> data = new XYChart.Data<>(5, "Charger");
        List<Node> reached = new ArrayList<>();

        SellerDashboardController.whenBarShown(data, reached::add);
        Region bar = new Region();
        data.setNode(bar);

        assertEquals(List.of(bar), reached);
    }
}
