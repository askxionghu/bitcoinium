
package com.veken0m.bitcoinium;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.analytics.tracking.android.EasyTracker;
import com.veken0m.bitcoinium.exchanges.Exchange;
import com.veken0m.bitcoinium.preferences.OrderbookPreferenceActivity;
import com.veken0m.utils.CurrencyUtils;
import com.veken0m.utils.Utils;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingMarketDataService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// import com.veken0m.utils.KarmaAdsUtils;

public class OrderbookActivity extends BaseActivity implements OnItemSelectedListener {

    private final static Handler mOrderHandler = new Handler();
    private Dialog dialog = null;
    private List<LimitOrder> listAsks = null;
    private List<LimitOrder> listBids = null;

    /**
     * List of preference variables
     */
    private static int pref_highlightHigh = 0;
    private static int pref_highlightLow = 0;
    private static int pref_orderbookLimiter = 0;
    private static Boolean pref_enableHighlight = true;
    private static Boolean pref_showCurrencySymbol = true;
    private static SharedPreferences prefs = null;

    private static CurrencyPair currencyPair = null;
    private static String exchangeName = "Bitstamp";
    private static Exchange exchange = null;
    private static Boolean exchangeChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.orderbook);

        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.show();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            exchangeName = extras.getString("exchange");
            exchange = new Exchange(this, exchangeName);
        } else {
            // TODO: generation error message
            exchangeName = "Bitstamp";
            exchange = new Exchange(this, "bitstamp");
        }

        readPreferences(this);
        createExchangeDropdown();
        //createCurrencyDropdown();
        //viewOrderbook();

        // KarmaAdsUtils.initAd(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.action_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_preferences:
                startActivity(new Intent(this, OrderbookPreferenceActivity.class));
                return true;
            case R.id.action_refresh:
                viewOrderbook();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.orderbook);

        if (listAsks != null && listBids != null) {
            createExchangeDropdown();
            createCurrencyDropdown();
            drawOrderbookUI();
        } else {
            // Fetch data
            viewOrderbook();
        }
    }

    /**
     * Fetch the OrderbookActivity and split into Ask/Bids lists
     */
    boolean getOrderBook() {

        if (listAsks != null && listBids != null) {
            listAsks.clear();
            listBids.clear();
        }

        final PollingMarketDataService marketData = ExchangeFactory.INSTANCE
                .createExchange(exchange.getClassName())
                .getPollingMarketDataService();

        OrderBook orderbook;
        try {
            orderbook = marketData.getOrderBook(currencyPair.baseCurrency, currencyPair.counterCurrency);
        } catch (IOException e) {
            return false;
        }

        if (orderbook != null) {
            // Limit OrderbookActivity orders drawn to improve performance
            int length = (orderbook.getAsks().size() < orderbook.getBids().size()) ? orderbook.getAsks().size() : orderbook.getBids().size();
            if (pref_orderbookLimiter != 0 && pref_orderbookLimiter < length)
                length = pref_orderbookLimiter;

            listAsks = orderbook.getAsks().subList(0, length);
            listBids = orderbook.getBids().subList(0, length);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Draw the Orders to the screen in a table
     */
    void drawOrderbookUI() {

        final TableLayout orderbookTable = (TableLayout) findViewById(R.id.orderlist);
        if (orderbookTable != null) {

            orderbookTable.removeAllViews();

            removeLoadingSpinner(R.id.orderbook_loadSpinner);
            setOrderBookHeader();

            boolean bBackGroundColor = true;

            String currencySymbolBTC, currencySymbol;
            currencySymbolBTC = currencySymbol = "";
            if (pref_showCurrencySymbol) {
                currencySymbolBTC = " " + currencyPair.baseCurrency;
                currencySymbol = Utils.getCurrencySymbol(currencyPair.counterCurrency);
            }

            for (int i = 0; i < listBids.size(); i++) {
                final TableRow newRow = new TableRow(this);
                final TextView tvAskAmount = new TextView(this);
                final TextView tvAskPrice = new TextView(this);
                final TextView tvBidPrice = new TextView(this);
                final TextView tvBidAmount = new TextView(this);

                final LimitOrder limitorderBid = listBids.get(i);
                final LimitOrder limitorderAsk = listAsks.get(i);

                float bidPrice = limitorderBid.getLimitPrice().getAmount().floatValue();
                float bidAmount = limitorderBid.getTradableAmount().floatValue();
                float askPrice = limitorderAsk.getLimitPrice().getAmount().floatValue();
                float askAmount = limitorderAsk.getTradableAmount().floatValue();

                tvBidAmount.setText(Utils.formatDecimal(bidAmount, 4, false) + currencySymbolBTC);
                tvBidAmount.setLayoutParams(Utils.symbolParams);
                tvBidAmount.setGravity(Gravity.CENTER);
                tvAskAmount.setText(Utils.formatDecimal(askAmount, 4, false) + currencySymbolBTC);
                tvAskAmount.setLayoutParams(Utils.symbolParams);
                tvAskAmount.setGravity(Gravity.CENTER);

                tvBidPrice.setText(currencySymbol + Utils.formatDecimal(bidPrice, 3, false));
                tvBidPrice.setLayoutParams(Utils.symbolParams);
                tvBidPrice.setGravity(Gravity.CENTER);
                tvAskPrice.setText(currencySymbol + Utils.formatDecimal(askPrice, 3, false));
                tvAskPrice.setLayoutParams(Utils.symbolParams);
                tvAskPrice.setGravity(Gravity.CENTER);

                // Text coloring for depth highlighting
                int bidTextColor = (pref_enableHighlight) ? depthColor(bidAmount) : Color.WHITE;
                int askTextColor = (pref_enableHighlight) ? depthColor(askAmount) : Color.WHITE;

                tvBidAmount.setTextColor(bidTextColor);
                tvBidPrice.setTextColor(bidTextColor);
                tvAskAmount.setTextColor(askTextColor);
                tvAskPrice.setTextColor(askTextColor);

                // Toggle background color
                if (bBackGroundColor = !bBackGroundColor)
                    newRow.setBackgroundColor(getResources().getColor(R.color.light_tableRow));

                newRow.addView(tvBidPrice);
                newRow.addView(tvBidAmount);
                newRow.addView(tvAskPrice);
                newRow.addView(tvAskAmount);

                orderbookTable.addView(newRow);
            }
        } else {
            failedToDrawUI();
        }

    }

    private void viewOrderbook() {
        if (Utils.isConnected(getApplicationContext())) {
            (new OrderbookThread()).start();
        } else {
            notConnected(R.id.bitcoincharts_loadSpinner);
        }
    }

    private class OrderbookThread extends Thread {

        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startLoading(R.id.orderlist, R.id.orderbook_loadSpinner);
                }
            });

            if (getOrderBook())
                mOrderHandler.post(mOrderView);
            else
                mOrderHandler.post(mError);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

        CurrencyPair prevCurrencyPair = currencyPair;
        String prevExchangeName = exchangeName;

        switch (parent.getId()){
            case R.id.orderbook_exchange_spinner:
                exchangeName = (String) parent.getItemAtPosition(pos);
                exchangeChanged = prevExchangeName != null && exchangeName != null && !exchangeName.equals(prevExchangeName);
                if (exchangeChanged){
                    exchange = new Exchange(this, exchangeName.replace("-","").replace(".",""));
                    currencyPair = CurrencyUtils.stringToCurrencyPair(prefs.getString(exchange.getIdentifier() + "CurrencyPref", exchange.getDefaultCurrency()));
                    createCurrencyDropdown();
                }
                break;
            case R.id.orderbook_currency_spinner:
                currencyPair = CurrencyUtils.stringToCurrencyPair((String) parent.getItemAtPosition(pos));
                break;
        }

        if (prevCurrencyPair != null && currencyPair != null && !currencyPair.equals(prevCurrencyPair) || exchangeChanged)
            viewOrderbook();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing...
    }

    private final Runnable mOrderView = new Runnable() {
        @Override
        public void run() {
            drawOrderbookUI();
        }
    };

    private final Runnable mError = new Runnable() {
        @Override
        public void run() {
            errorOccured();
        }
    };

    private void errorOccured() {

        removeLoadingSpinner(R.id.orderbook_loadSpinner);

        if (dialog == null || !dialog.isShowing()) {
            // Display error Dialog
            Resources res = getResources();
            String text = String.format(res.getString(R.string.connectionError),
                    res.getString(R.string.orderbook), exchange.getExchangeName());
            dialog = Utils.errorDialog(this, text);
        }
    }

    private void failedToDrawUI() {

        removeLoadingSpinner(R.id.orderbook_loadSpinner);
        // Display error Dialog
        if (dialog == null || !dialog.isShowing())
            dialog = Utils.errorDialog(this, getString(R.string.errBitcoinAverageTable), getString(R.string.error));
    }

    void setOrderBookHeader() {
        TextView orderBookHeader = (TextView) findViewById(R.id.orderbook_header);
        orderBookHeader.setText(exchange.getExchangeName() + " " + currencyPair.toString());
    }

    void createExchangeDropdown() {

        // Re-populate the dropdown menu
        String[] exchanges = getResources().getStringArray(R.array.exchangesOrderbook);
        Spinner spinner = (Spinner) findViewById(R.id.orderbook_exchange_spinner);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, exchanges);

        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);
        spinner.setOnItemSelectedListener(this);

        int index = Arrays.asList(exchanges).indexOf(exchange.getExchangeName());
        spinner.setSelection(index);
    }

    void createCurrencyDropdown() {
        // Re-populate the dropdown menu
        int arrayId = getResources().getIdentifier(exchange.getIdentifier() + "currencies", "array", this.getPackageName());
        String[] currencies = getResources().getStringArray(arrayId);

        Spinner spinner = (Spinner) findViewById(R.id.orderbook_currency_spinner);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, currencies);

        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);
        spinner.setOnItemSelectedListener(this);

        if(exchangeChanged){
            int index = Arrays.asList(currencies).indexOf(currencyPair.toString());
            spinner.setSelection(index);
        }
    }

    int depthColor(float amount) {
        if ((int) amount >= pref_highlightHigh)
            return Color.GREEN;
        else if ((int) amount < pref_highlightLow)
            return Color.RED;
        else
            return Color.YELLOW;
    }

    private static void readPreferences(Context context) {

        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        pref_enableHighlight = prefs.getBoolean("highlightPref", true);
        pref_highlightHigh = Integer.parseInt(prefs.getString("depthHighlightUpperPref", "10"));
        pref_highlightLow = Integer.parseInt(prefs.getString("depthHighlightLowerPref", "1"));
        currencyPair = CurrencyUtils.stringToCurrencyPair(prefs.getString(exchange.getIdentifier() + "CurrencyPref", exchange.getDefaultCurrency()));
        pref_showCurrencySymbol = prefs.getBoolean("showCurrencySymbolPref",
                true);
        try {
            pref_orderbookLimiter = Integer.parseInt(prefs.getString("orderbookLimiterPref", "100"));
        } catch (Exception e) {
            pref_orderbookLimiter = 100;
            // If preference is not set a valid integer set to "100"
            Editor editor = prefs.edit();
            editor.putString("orderbookLimiterPref", "100");
            editor.commit();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("googleAnalyticsPref", false)) {
            EasyTracker.getInstance(this).activityStart(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        readPreferences(this);
        createExchangeDropdown();
        viewOrderbook();
    }

    @Override
    public void onStop() {
        super.onStop();
        EasyTracker.getInstance(this).activityStop(this);
    }

}
