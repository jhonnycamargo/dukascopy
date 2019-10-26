package singlejartest;

import com.dukascopy.api.*;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IOrder.State;
import com.dukascopy.api.feed.*;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.util.*;
import java.util.*;

public class Strategy implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
  
        Instrument instrument = Instrument.EURUSD;
        Period period = Period.ONE_MIN;
        IBar prevBar = history.getBar(instrument, period, OfferSide.BID, 1);
        int candlesBefore = 1;
        int candleAfter = 0;
        int shift = 1;
//        
//        double[] currentMACD = this.indicators.macd(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 12, 26, 9, 0);
//        double[] previosMACD = this.indicators.macd(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 12, 26, 9, 1);
//        double rsi = this.indicators.rsi(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 14, 0);
//        List<IOrder> orders = this.history.getOpenOrders(instrument, 1L, 2L);
//        IOrder order = orders.get(0);
//        IAccount account = this.context.getAccount();
//        IDataService data = this.context.getDataService();
//        data.
        
//        
////        ITick tick = history.getLastTick(Instrument.EURUSD);
////        double price = tick.getAsk() + 10 * Instrument.EURUSD.getPipValue();
////        long time = System.currentTimeMillis() + 5 * 60 * 1000;
////        IOrder order = engine.submitOrder("abc", Instrument.EURUSD, IEngine.OrderCommand.BUYSTOP, 0.1, price, 10, 0, 0, time);
////        order.waitForUpdate(3000, IOrder.State.OPENED);
//        
//        IOrder order = engine.getOrders().get(0);
//
//        order.setComment("comment"+ System.currentTimeMillis());
        

    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
        console.getOut().println(message);
        for(IMessage.Reason reason : message.getReasons()) {
            console.getOut().println(reason);
        }
    }

    public void onStop() throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    }
}