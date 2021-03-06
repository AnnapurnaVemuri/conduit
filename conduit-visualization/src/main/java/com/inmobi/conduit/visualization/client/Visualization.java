package com.inmobi.conduit.visualization.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ShowRangeEvent;
import com.google.gwt.event.logical.shared.ShowRangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.datepicker.client.DatePicker;
import com.inmobi.conduit.visualization.client.util.ClientDataHelper;
import com.inmobi.conduit.visualization.client.util.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Visualization implements EntryPoint, ClickHandler {

  private ListBox streamsList, clusterList;
  private TextBox startTime, endtime;
  private VerticalPanel startTimeVPanel, endTimeVPanel, streamVPanel,
      clusterVPanel, headerVPanel;
  private ListBox stTimeHour, stTimeMinute, edTimeHour, edTimeMinute;
  private DatePicker stDatePicker, endDatePicker;
  private HorizontalPanel etTimeHPanel;
  private HorizontalPanel stTimeHPanel;
  private HorizontalPanel filterPanel;
  private Label stTimeLabel, etTimeLabel, streamLabel, clusterLabel,
      currentTimeLabel;
  private PopupPanel stcalendarPopup, etcalendarPopup;
  private boolean isDevMode = false;

  List<String> streams = new ArrayList<String>(), clusters =
      new ArrayList<String>();
  private Map<String, String> clientConfig;
  private int rolledUpTillDays;
  DataServiceWrapper serviceInstance = new DataServiceWrapper();

  public void onModuleLoad() {
    buildStreamsAndClustersList();
  }

  private boolean checkParametersNull() {
    String startTime = Window.Location.getParameter(ClientConstants.QUERY_FROM_TIME);
    String endTime = Window.Location.getParameter(ClientConstants.QUERY_TO_TIME);
    String cluster = Window.Location.getParameter(ClientConstants.QUERY_CLUSTER);
    String stream = Window.Location.getParameter(ClientConstants.QUERY_STREAM);
    if (startTime != null && endTime != null && cluster != null &&
        stream != null) {
      return false;
    }
    return true;
  }

  private void buildStreamsAndClustersList() {
    System.out.println("Building stream and cluster list...");
    serviceInstance.getStreamAndClusterList(new AsyncCallback<String>() {

      public void onFailure(Throwable caught) {
        caught.printStackTrace();
      }

      public void onSuccess(String result) {
        System.out.println("Completed building stream and cluster list");
        streams.addAll(
            ClientDataHelper.getInstance()
                .getStreamsListFromLoadMainPanelResponse(result));
        clusters.addAll(
            ClientDataHelper.getInstance()
                .getClusterListFromLoadMainPanelResponse(result));
        clientConfig = ClientDataHelper.getInstance()
            .getClientConfigLoadMainPanelResponse(result);
        rolledUpTillDays = Integer.parseInt(clientConfig.get(ClientConstants
            .ROLLEDUP_TILL_DAYS));
        loadMainPanel();
      }
    });
  }

  private void loadMainPanel() {
    System.out.println("Loading main panel...");
    loadHeader();
    loadFilterPanel();
    RootPanel.get("headerContainer").add(headerVPanel);
    RootPanel.get("filterContainer").add(filterPanel);
    System.out.println("Loaded main panel");

    checkIfGWTDevMode();
    String stream, cluster, startTime, endTime, selectedTab;
    if (checkParametersNull()) {
      System.out.println("Loading default settings");
      stream = "All";
      cluster = "All";
      startTime = DateUtils.getPreviousDayString();
      endTime = DateUtils.incrementAndGetTimeAsAuditDateFormatString(startTime, 60);
      selectedTab = "1";
    } else {
      System.out.println("Retrieving parameters from URL");
      startTime = Window.Location.getParameter(ClientConstants.QUERY_FROM_TIME);
      endTime = Window.Location.getParameter(ClientConstants.QUERY_TO_TIME);
      cluster = Window.Location.getParameter(ClientConstants.QUERY_CLUSTER);
      stream = Window.Location.getParameter(ClientConstants.QUERY_STREAM);
      selectedTab = Window.Location.getParameter(ClientConstants.SELECTED_TAB);
    }
    setSelectedParameterValues(startTime, endTime, cluster, stream);
    sendRequest(startTime, endTime, cluster, stream, selectedTab);
  }

  private void checkIfGWTDevMode() {
    String host = Window.Location.getParameter(ClientConstants
        .GWT_DEVLOPMENT_MODE);
    if (host == null || host.length() == 0)
      isDevMode = false;
    else
      isDevMode = true;
  }

  private void setSelectedParameterValues(String stTime, String endTime,
                                          String cluster, String stream) {
    startTime.setText(DateUtils.getBaseDateStringFromAuditDateFormat(stTime));
    endtime.setText(DateUtils.getBaseDateStringFromAuditDateFormat(endTime));
    stDatePicker.setValue(DateUtils.getDateFromAuditDateFormatString(stTime));
    endDatePicker.setValue(DateUtils.getDateFromAuditDateFormatString(endTime));
    setSelectedInListBox(stTimeHour, DateUtils.getHourFromAuditDateFormatString(stTime));
    setSelectedInListBox(stTimeMinute,
        DateUtils.getMinuteFromAuditDateFormatString(stTime));
    setSelectedInListBox(edTimeHour,
        DateUtils.getHourFromAuditDateFormatString(endTime));
    setSelectedInListBox(edTimeMinute,
        DateUtils.getMinuteFromAuditDateFormatString(endTime));
    if (DateUtils.checkSelectedDateRolledUp(stTime, rolledUpTillDays, true)) {
      stTimeMinute.setEnabled(false);
    } else {
      stTimeMinute.setEnabled(true);
    }
    if (DateUtils.checkSelectedDateRolledUp(endTime, rolledUpTillDays, true)) {
      edTimeMinute.setEnabled(false);
    } else {
      edTimeMinute.setEnabled(true);
    }
    setSelectedInListBox(clusterList, cluster);
    setSelectedInListBox(streamsList, stream);
  }

  private void setSelectedInListBox(ListBox listBox, String selectedString) {
    int selectedIndex = 0;
    for (int i = 0; i < listBox.getItemCount(); i++) {
      if (listBox.getItemText(i).equalsIgnoreCase(selectedString)) {
        selectedIndex = i;
        break;
      }
    }
    listBox.setSelectedIndex(selectedIndex);
  }

  private void loadHeader() {
    headerVPanel = new VerticalPanel();
    currentTimeLabel = new Label(DateUtils.getCurrentTimeStringInGMT());
    HTML heading = new HTML("<h1>Conduit Dashboard</h1>");
    heading.getElement().setId("heading");
    currentTimeLabel.getElement().setId("currentTimeLabel");
    headerVPanel.getElement().setId("header");
    headerVPanel.add(heading);
    headerVPanel.add(currentTimeLabel);
  }

  private void loadFilterPanel() {
    stcalendarPopup = new PopupPanel(true);
    etcalendarPopup = new PopupPanel(true);
    filterPanel = new HorizontalPanel();
    startTime = new TextBox();
    endtime = new TextBox();
    streamsList = new ListBox();
    clusterList = new ListBox();
    stDatePicker = new DatePicker();
    endDatePicker = new DatePicker();
    stTimeHour = new ListBox();
    stTimeMinute = new ListBox();
    edTimeHour = new ListBox();
    edTimeMinute = new ListBox();
    etTimeHPanel = new HorizontalPanel();
    stTimeHPanel = new HorizontalPanel();
    endTimeVPanel = new VerticalPanel();
    startTimeVPanel = new VerticalPanel();
    streamVPanel = new VerticalPanel();
    clusterVPanel = new VerticalPanel();
    stTimeLabel = new Label("Start Time");
    etTimeLabel = new Label("End Time");
    streamLabel = new Label("Stream");
    clusterLabel = new Label("Cluster");
    Button goButton = new Button("Go");

    startTime.getElement().setId("stTextBox");
    endtime.getElement().setId("etTextBox");
    streamsList.getElement().setId("streamDropDown");
    clusterList.getElement().setId("clusterDropDown");
    goButton.getElement().setId("goButton");
    filterPanel.getElement().setId("filterPanel");
    stDatePicker.setStyleName("calendar-popup");
    endDatePicker.setStyleName("calendar-popup");

    streamVPanel.add(streamLabel);
    streamVPanel.add(streamsList);
    clusterVPanel.add(clusterLabel);
    clusterVPanel.add(clusterList);
    stcalendarPopup.add(stDatePicker);
    etcalendarPopup.add(endDatePicker);
    stcalendarPopup.setGlassEnabled(true);
    etcalendarPopup.setGlassEnabled(true);

    startTime.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        stcalendarPopup.center();
      }
    });
    endtime.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        etcalendarPopup.center();
      }
    });
    stDatePicker.addValueChangeHandler(new ValueChangeHandler<Date>() {
      public void onValueChange(ValueChangeEvent<Date> event) {
        Date selectedDate = event.getValue();
        String selectedDateString = DateUtils.BASE_DATE_FORMATTER.format(selectedDate);
        startTime.setText(selectedDateString);
        if (DateUtils.checkSelectedDateRolledUp(selectedDate,
            Integer.parseInt(clientConfig.get(ClientConstants.ROLLEDUP_TILL_DAYS)))) {
          stTimeMinute.setSelectedIndex(1);
          stTimeMinute.setEnabled(false);
        } else {
          stTimeMinute.setEnabled(true);
        }
        stcalendarPopup.hide();
      }
    });
    endDatePicker.addValueChangeHandler(new ValueChangeHandler<Date>() {
      public void onValueChange(ValueChangeEvent<Date> event) {
        Date selectedDate = event.getValue();
        String selectedDateString = DateUtils.BASE_DATE_FORMATTER.format(selectedDate);
        endtime.setText(selectedDateString);
        if (DateUtils.checkSelectedDateRolledUp(selectedDate, rolledUpTillDays)) {
          edTimeMinute.setSelectedIndex(1);
          edTimeMinute.setEnabled(false);
        } else {
          edTimeMinute.setEnabled(true);
        }
        etcalendarPopup.hide();
      }
    });
    stDatePicker.addShowRangeHandler(new ShowRangeHandler<Date>()
    {
      @Override
      public void onShowRange(final ShowRangeEvent<Date> dateShowRangeEvent)
      {
        Date maxStartDate = DateUtils.getDateFromBaseDateFormatString
            (clientConfig.get(ClientConstants.MAX_START_TIME));
        Date d = DateUtils.getDateWithZeroTime(dateShowRangeEvent.getStart());
        while (d.before(maxStartDate)) {
          stDatePicker.setTransientEnabledOnDates(false, d);
          d = DateUtils.getNextDay(d);
        }
        Date currentDate = new Date();
        d = DateUtils.getDateWithZeroTime(dateShowRangeEvent.getEnd());
        while (d.after(currentDate)) {
          stDatePicker.setTransientEnabledOnDates(false, d);
          d = DateUtils.getPreviousDay(d);
        }
      }
    });
    endDatePicker.addShowRangeHandler(new ShowRangeHandler<Date>()
    {
      @Override
      public void onShowRange(final ShowRangeEvent<Date> dateShowRangeEvent)
      {
        Date maxStartDate = DateUtils.getDateFromBaseDateFormatString
            (clientConfig.get(ClientConstants.MAX_START_TIME));
        Date d = DateUtils.getDateWithZeroTime(dateShowRangeEvent.getStart());
        while (d.before(maxStartDate)) {
          endDatePicker.setTransientEnabledOnDates(false, d);
          d = DateUtils.getNextDay(d);
        }
        Date currentDate = new Date();
        d = DateUtils.getDateWithZeroTime(dateShowRangeEvent.getEnd());
        while (d.after(currentDate)) {
          endDatePicker.setTransientEnabledOnDates(false, d);
          d = DateUtils.getPreviousDay(d);
        }
      }
    });
    startTime.setWidth("100px");
    endtime.setWidth("100px");
    stTimeHour.addItem("HH");
    stTimeHour.setWidth("50px");
    stTimeMinute.addItem("MM");
    stTimeMinute.setWidth("50px");
    edTimeHour.addItem("HH");
    edTimeHour.setWidth("50px");
    edTimeMinute.addItem("MM");
    edTimeMinute.setWidth("50px");
    fillListBox(stTimeHour, 24);
    fillListBox(stTimeMinute, 60);
    fillListBox(edTimeHour, 24);
    fillListBox(edTimeMinute, 60);

    streamsList.addItem("<Select Stream>");
    clusterList.addItem("<Select Cluster>");
    for (String stream : streams) {
      streamsList.addItem(stream);
    }
    for (String cluster : clusters) {
      clusterList.addItem(cluster);
    }
    goButton.addClickHandler(this);

    stTimeHPanel.add(startTime);
    stTimeHPanel.add(stTimeHour);
    stTimeHPanel.add(stTimeMinute);
    startTimeVPanel.add(stTimeLabel);
    startTimeVPanel.add(stTimeHPanel);

    etTimeHPanel.add(endtime);
    etTimeHPanel.add(edTimeHour);
    etTimeHPanel.add(edTimeMinute);
    endTimeVPanel.add(etTimeLabel);
    endTimeVPanel.add(etTimeHPanel);

    filterPanel.add(startTimeVPanel);
    filterPanel.add(endTimeVPanel);
    filterPanel.add(streamVPanel);
    filterPanel.add(clusterVPanel);
    filterPanel.add(goButton);
  }

  private void fillListBox(ListBox listBox, int value) {
    for (int i = 0; i < value; i++) {
      if (i < 10) {
        listBox.addItem("0" + String.valueOf(i));
      } else {
        listBox.addItem(String.valueOf(i));
      }
    }
  }

  public void onClick(ClickEvent event) {
    String stTime = DateUtils.constructDateString(startTime.getText(),
        stTimeHour.getItemText(stTimeHour.getSelectedIndex()),
        stTimeMinute.getItemText(stTimeMinute.getSelectedIndex()));
    String edTime = DateUtils.constructDateString(endtime.getText(),
        edTimeHour.getItemText(edTimeHour.getSelectedIndex()),
        edTimeMinute.getItemText(edTimeMinute.getSelectedIndex()));
    if (!validateParameters(stTime, edTime)) {
      return;
    }
    disableFilterSelection();
    sendRequest(stTime, edTime, clusterList.getItemText(clusterList
        .getSelectedIndex()), streamsList.getItemText(streamsList
        .getSelectedIndex()), null);
  }

  public void sendRequest(final String stTime, final String edTime,
                          final String selectedCluster,
                          final String selectedStream,
                          final String selectedTab) {
    Integer defaultTabId = 1;
    if(selectedTab != null)
      defaultTabId =  Integer.parseInt(selectedTab);
    final Integer selectedTabId = defaultTabId;
    saveHistory(stTime, edTime, selectedCluster, selectedStream, selectedTabId);
    System.out.println("Sending request to load graph");
    System.out.println("Start:"+stTime+"\nEnd:"+edTime+"\nCluster" +
        ":"+selectedCluster+"\nStream:"+selectedStream+"\nTab " +
        "selected:"+selectedTabId);
    clearAndShowLoadingSymbol();
    String clientJson = ClientDataHelper.getInstance()
        .setGraphDataRequest(stTime, edTime, selectedStream, selectedCluster);
    serviceInstance.getData(clientJson, new AsyncCallback<String>() {

      public void onFailure(Throwable caught) {
        caught.printStackTrace();
      }

      public void onSuccess(String result) {
        String nodesJson = ClientDataHelper.getInstance()
            .getJsonStringFromGraphDataResponse(result);
        Map<String, Integer> tierLatencyMap = ClientDataHelper.getInstance()
            .getTierLatencyObjListFromResponse(result);
        if (tierLatencyMap != null) {
          Integer pLatency = tierLatencyMap.get(ClientConstants.PUBLISHER);
          Integer aLatency = tierLatencyMap.get(ClientConstants.AGENT);
          Integer cLatency = tierLatencyMap.get(ClientConstants.COLLECTOR);
          Integer hLatency = tierLatencyMap.get(ClientConstants.HDFS);
          Integer lLatency = tierLatencyMap.get(ClientConstants.LOCAL);
          Integer mergeLatency = tierLatencyMap.get(ClientConstants.MERGE);
          Integer mirrorLatency = tierLatencyMap.get(ClientConstants.MIRROR);
          if ( pLatency == null )
            pLatency = -1;
          if ( aLatency == null )
            aLatency = -1;
          if ( cLatency == null )
            cLatency = -1;
          if ( hLatency == null )
            hLatency = -1;
          if ( lLatency == null )
            lLatency = -1;
          if ( mergeLatency == null )
            mergeLatency = -1;
          if ( mirrorLatency == null )
            mirrorLatency = -1;
          setTierLatencyValues(pLatency, aLatency, cLatency,hLatency,
              lLatency, mergeLatency, mirrorLatency);
        }
        drawGraph(nodesJson, selectedCluster, selectedStream, stTime, edTime,
            selectedTabId, Integer.parseInt(clientConfig
            .get(ClientConstants.PUBLISHER)), Integer.parseInt(clientConfig
            .get(ClientConstants.AGENT)), Integer.parseInt(clientConfig.get
            (ClientConstants.VIP)), Integer.parseInt(clientConfig.get
            (ClientConstants.COLLECTOR)), Integer.parseInt(clientConfig.get
            (ClientConstants.HDFS)), Integer.parseInt(clientConfig.get
            (ClientConstants.LOCAL)), Integer.parseInt(clientConfig.get
            (ClientConstants.MERGE)), Integer.parseInt(clientConfig.get
            (ClientConstants.MIRROR)), Float.parseFloat(clientConfig.get
            (ClientConstants.PERCENTILE_FOR_SLA)),
            Float.parseFloat(clientConfig.get(ClientConstants
                .PERCENTAGE_FOR_LOSS)), Float.parseFloat(clientConfig.get
            (ClientConstants.PERCENTAGE_FOR_WARN)),
            Integer.parseInt(clientConfig.get(ClientConstants
                .LOSS_WARN_THRESHOLD_DIFF)), isDevMode);
        enableFilterSelection();
      }
    });
  }

  private void enableFilterSelection() {
    startTime.setEnabled(true);
    stTimeHour.setEnabled(true);
    if (DateUtils.checkSelectedDateRolledUp(startTime.getText(),
        rolledUpTillDays, false)) {
      stTimeMinute.setEnabled(false);
    } else {
      stTimeMinute.setEnabled(true);
    }

    endtime.setEnabled(true);
    edTimeHour.setEnabled(true);
    if (DateUtils.checkSelectedDateRolledUp(endtime.getText(),
        rolledUpTillDays, false)) {
      edTimeMinute.setEnabled(false);
    } else {
      edTimeMinute.setEnabled(true);
    }

    streamsList.setEnabled(true);
    clusterList.setEnabled(true);

  }

  private void disableFilterSelection() {
    startTime.setEnabled(false);
    stTimeHour.setEnabled(false);
    stTimeMinute.setEnabled(false);

    endtime.setEnabled(false);
    edTimeHour.setEnabled(false);
    edTimeMinute.setEnabled(false);

    streamsList.setEnabled(false);
    clusterList.setEnabled(false);
  }

  private native void saveHistory(String stTime, String edTime,
                            String selectedCluster, String selectedStream,
                            int selectedTab)/*-{
    $wnd.saveHistory(selectedStream, selectedCluster, selectedTab, stTime,
    edTime);
  }-*/;

  private native void setTierLatencyValues(int publisherLatency,
                                           int agentLatency,
                                           int collectorLatency,
                                           int hdfsLatency,
                                           int localLatency,
                                           int mergeLatency,
                                           int mirrorLatency)/*-{
    $wnd.setTierLatencyValues(publisherLatency, agentLatency,
    collectorLatency, hdfsLatency, localLatency, mergeLatency, mirrorLatency);
  }-*/;

  private boolean validateParameters(String stTime, String edTime) {
    if (DateUtils.checkIfFutureDate(stTime)) {
      Window.alert("Future start time is not allowed");
      return false;
    } else if (DateUtils.checkIfFutureDate(edTime)) {
      Window.alert("Future end time is not allowed");
      return false;
    } else if (DateUtils.checkStAfterEt(stTime, edTime)) {
      Window.alert("Start time is after end time");
      return false;
    } else if (DateUtils.checkTimeInterval(stTime, edTime,
        clientConfig.get(ClientConstants.MAX_TIME_INT_IN_HRS))) {
      Window.alert("Time Range is more than allowed "+clientConfig.get
          (ClientConstants.MAX_TIME_INT_IN_HRS)+" hrs");
      return false;
    } else if (streamsList.getSelectedIndex() < 1) {
      Window.alert("Select Stream");
      return false;
    } else if (clusterList.getSelectedIndex() < 1) {
      Window.alert("Select Cluster");
      return false;
    }
    return true;
  }

  private native void clearAndShowLoadingSymbol()/*-{
    $wnd.clearSvgAndAddLoadSymbol();
  }-*/;

  private native void drawGraph(String result, String cluster, String stream,
                                String start, String end,
                                Integer selectedTabID,
                                Integer publisherSla, Integer agentSla,
                                Integer vipSla, Integer collectorSla,
                                Integer hdfsSla,
                                Integer localSla,
                                Integer mergeSla,
                                Integer mirrorSla, Float percentileForSla,
                                Float percentageForLoss,
                                Float percentageForWarn,
                                Integer lossWarnThresholdDiff,
                                boolean isDevMode)/*-{
    $wnd.drawGraph(result, cluster, stream, start, end, selectedTabID,
    publisherSla, agentSla, vipSla, collectorSla, hdfsSla, localSla,
    mergeSla, mirrorSla, percentileForSla, percentageForLoss,
    percentageForWarn, lossWarnThresholdDiff, isDevMode);
  }-*/;
}