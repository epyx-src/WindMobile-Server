/*******************************************************************************
 * Copyright (c) 2011 epyx SA.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package ch.windmobile.server;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import ch.windmobile.Assert;
import ch.windmobile.server.datasourcemodel.DataSourceException;
import ch.windmobile.server.datasourcemodel.WindMobileDataSource;
import ch.windmobile.server.datasourcemodel.xml.Chart;
import ch.windmobile.server.datasourcemodel.xml.Serie;
import ch.windmobile.server.datasourcemodel.xml.StationData;
import ch.windmobile.server.datasourcemodel.xml.StationInfo;
import ch.windmobile.server.datasourcemodel.xml.StationUpdateTime;
import ch.windmobile.server.datasourcemodel.xml.Status;

public abstract class ITTestDataSource extends AbstractTestNGSpringContextTests {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm ZZ").withOffsetParsed();

    @Autowired
    private WindMobileDataSource dataSource;

    @DataProvider(name = "stationIds")
    public Object[][] stationIds() throws DataSourceException {
        List<StationInfo> stationInfos = dataSource.getStationInfoList(false);
        Object[][] results = new Object[stationInfos.size()][1];

        for (int i = 0; i < stationInfos.size(); i++) {
            results[i][0] = stationInfos.get(i).getId();
        }
        return results;
    }

    void testLastUpdate(DateTime lastUpdate, boolean checkDataValidity) {
        Assert.assertNotNull(lastUpdate, "lastUpdate is null");
        DateTime now = new DateTime();
        Assert.assertFalse(lastUpdate.isAfter(now), "lastUpdate is in the future");
        if (checkDataValidity) {
            Assert.assertTrue(lastUpdate.plusHours(18).isAfter(now), "lastUpdate is more than 18 hours old");
        }
    }

    @Test(dataProvider = "stationIds")
    public void testGetLastUpdate(String stationId) throws DataSourceException {
        StationUpdateTime lastUpdate = dataSource.getLastUpdate(stationId);
        log.info("getLastUpdate returns " + lastUpdate.getLastUpdate().toString(dateFormatter));

        testLastUpdate(lastUpdate.getLastUpdate(), false);
    }

    void testStationId(String stationId) {
        Assert.assertNotEmpty(stationId, "Not a valid id");
        Assert.assertTrue(stationId.contains(":") == false, "Not a valid id");
    }

    void testAltitude(int altitude) {
        if (altitude > 0 && altitude < 3500) {
            return;
        }
        Assert.fail("Not a valid altitude");
    }

    void testCoordinate(double coordinate) {
        Assert.assertTrue(coordinate != 0.0, "Not a valid coordinate");
    }

    void testStationInfoContent(StationInfo info) {
        log.info("StationInfo");
        log.info("    stationId --> " + info.getId());
        log.info("    shortName --> " + info.getShortName());
        log.info("    name --> " + info.getName());

        testStationId(info.getId());
        Assert.assertNotEmpty(info.getShortName(), "No short name");
        Assert.assertNotEmpty(info.getName(), "No name");
        Assert.assertTrue(info.getShortName().length() <= info.getName().length(), "Not a short name");

        testAltitude(info.getAltitude());
        testCoordinate(info.getWgs84Latitude());
        testCoordinate(info.getWgs84Longitude());
    }

    @Test(dataProvider = "stationIds")
    public void testGetStationInfos(String stationId) throws DataSourceException {
        testStationInfoContent(dataSource.getStationInfo(stationId));
    }

    float testWindValue(float value) {
        Assert.assertTrue(value >= 0.0 && value < 150.0, "Wind value out of range");
        return value;
    }

    void testStationDataContent(StationData data) {
        log.info("StationData");
        log.info("    stationId --> " + data.getStationId());
        log.info("    lastUpdate --> " + data.getLastUpdate());
        log.info("    status --> " + data.getStatus());
        log.info("    windAverage --> " + testWindValue(data.getWindAverage()));
        log.info("    windMax --> " + testWindValue(data.getWindMax()));
        log.info("    windHistoryMin --> " + testWindValue(data.getWindHistoryMin()));
        log.info("    windHistoryAverage --> " + testWindValue(data.getWindHistoryAverage()));
        log.info("    windHistoryMax --> " + testWindValue(data.getWindHistoryMax()));

        testStationId(data.getStationId());
        testLastUpdate(data.getLastUpdate(), (data.getStatus() == Status.GREEN));

        Assert.assertNotNull(data.getStatus().value());

        Assert.assertTrue(data.getWindAverage().floatValue() <= data.getWindMax().floatValue());
        Assert.assertTrue(data.getWindHistoryMin().floatValue() <= data.getWindHistoryAverage().floatValue());
        Assert.assertTrue(data.getWindHistoryAverage().floatValue() <= data.getWindHistoryMax().floatValue());

        Assert.assertTrue(data.getWindHistoryAverage() > 0, "Wind history is 0");
    }

    @Test(dataProvider = "stationIds")
    public void testGetStationDatas(String stationId) throws DataSourceException {
        testStationDataContent(dataSource.getStationData(stationId));
    }

    void testWindChart(String stationId, int chartDuration) throws DataSourceException {
        Chart windChart = dataSource.getWindChart(stationId, chartDuration);
        Assert.assertEquals((int) windChart.getDuration(), chartDuration);

        Serie windAverageSerie = windChart.getSeries().get(0);
        Assert.assertEquals(windAverageSerie.getName(), "windAverage");
        Assert.assertTrue(windAverageSerie.getPoints().size() > 0);

        Serie windMaxSerie = windChart.getSeries().get(1);
        Assert.assertEquals(windMaxSerie.getName(), "windMax");
        Assert.assertTrue(windMaxSerie.getPoints().size() > 0);
    }

    int[] windChartDuration() {
        return new int[] { 7200, 9600, 1200 };
    }

    @Test(dataProvider = "stationIds")
    public void testGetWindChart(String stationId) throws DataSourceException {
        for (int duration : windChartDuration()) {
            testWindChart(stationId, duration);

        }
    }
}
