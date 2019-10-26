package singlejartest;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IMessage.Type;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IOrder.State;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.feed.IRenkoBar;

public class RenkoCrossingKijunSenStrategy implements IStrategy {

	private static final Instrument DEFAULT_INSTRUMENT = Instrument.XAUUSD;
	private IHistory history;
	private IEngine engine;

	private OfferSide side = OfferSide.BID;
	private int brickSize = 10;

	List<RenkoBar> eurusdRenkos = new ArrayList<>();
	private static final int KIJUN_DAYS = 3;
	private static final int SL_PIPS = 45;
	private static final int TP_PIPS = 1800;
	private static final int TS_PIPS = 15;
	private static final int CLOSE_TP_PIPS = 25;

	private RenkoBar renko = null;

	private IAccount account;

	private boolean trade = true;
	private boolean myClose;

	@Override
	public void onStart(IContext context) throws JFException {
		history = context.getHistory();
		this.engine = context.getEngine();
		this.account = context.getAccount();
	}

	private void process(RenkoBar renko, Instrument instrument) throws JFException {
		List<RenkoBar> process;
		if (DEFAULT_INSTRUMENT.equals(instrument)) {
			eurusdRenkos.add(renko);
			process = eurusdRenkos;
			if (process.size() < KIJUN_DAYS + 1) {
				return;
			}
		} else {
			return;
		}

		double kijunValue = calculateKijunSen(process);
		double prevKijunValue = calculateKijunSen(process.subList(0, process.size() - 1));

		if (process.get(process.size() - 2).getClose() > prevKijunValue && renko.getClose() < kijunValue) {
			continueSelling(instrument);
		} else if (process.get(process.size() - 2).getClose() < prevKijunValue && renko.getClose() > kijunValue) {
			continueBuying(instrument);
		}
	}

	private void processClose(IOrder order) throws JFException {
		List<RenkoBar> process;
		Instrument instrument = order.getInstrument();
		if (DEFAULT_INSTRUMENT.equals(instrument)) {
			process = eurusdRenkos;
			if (process.size() < KIJUN_DAYS + 1) {
				return;
			}
		} else {
			return;
		}

		double kijunValue = calculateKijunSen(process);
		double prevKijunValue = calculateKijunSen(process.subList(0, process.size() - 1));

		if (process.get(process.size() - 2).getClose() > prevKijunValue
				&& process.get(process.size() - 1).getClose() < kijunValue) {
			openSellPosition(instrument);
		} else if (process.get(process.size() - 2).getClose() < prevKijunValue
				&& process.get(process.size() - 1).getClose() > kijunValue) {
			continueBuying(instrument);
		} else {
			if (order.isLong()) {
				openBuyPosition(order);
			} else {
				openSellPosition(order);
			}
		}
	}

	private double calculateKijunSen(List<RenkoBar> process) {
		double high = Double.NEGATIVE_INFINITY;
		double low = Double.POSITIVE_INFINITY;
		for (int i = 1; i <= KIJUN_DAYS; i++) {
			RenkoBar r = process.get(process.size() - i);
			high = r.getHigh() > high ? r.getHigh() : high;
			low = r.getLow() < low ? r.getLow() : low;
		}
		return (high + low) / 2;
	}

	private void continueSelling(Instrument instrument) throws JFException {
		if (openPositions(instrument)) {
			closeOpenPositions(instrument, true);
		} else {
			openSellPosition(instrument);
		}
	}

	private void continueBuying(Instrument instrument) throws JFException {
		if (openPositions(instrument)) {
			closeOpenPositions(instrument, false);
		} else {
			openBuyPosition(instrument);
		}
	}

	private double getMinAmount() {
		double amount = this.account.getEquity() * 0.0001;

		return amount > 500.0 ? 500.0 : amount;
	}

	private String getLabel(Instrument instrument) {
		String label = instrument.name();
		label = label.substring(0, 2) + label.substring(3, 5);
		label = label.toLowerCase();
		label = label + UUID.randomUUID().toString().replaceAll("-", "_");

		return label;
	}

	private void closeOpenPositions(Instrument instrument, boolean buy) throws JFException {
		List<IOrder> orders = this.engine.getOrders(instrument);
		for (IOrder order : orders) {
			if ((State.OPENED.equals(order.getState()) || State.FILLED.equals(order.getState()))
					&& ((buy && order.isLong()) || (!buy && !order.isLong()))) {
				order.close();
			}
		}
	}

	private boolean openPositions(Instrument instrument) throws JFException {
		List<IOrder> orders = this.engine.getOrders(instrument);
		for (IOrder order : orders) {
			if ((State.OPENED.equals(order.getState()) || State.FILLED.equals(order.getState()))) {
				return true;
			}
		}

		return false;
	}

	private boolean validateLastCandlestick(IBar lastBidBar) {
		long timeCandle = lastBidBar.getTime();
		Instant i = Instant.ofEpochMilli(timeCandle);
		ZonedDateTime z = ZonedDateTime.ofInstant(i, ZoneId.of("GMT"));

		return z.getDayOfMonth() >= 25 || z.getDayOfWeek().equals(DayOfWeek.FRIDAY);
	}

	private void openBuyPosition(Instrument instrument) throws JFException {
		if (this.trade) {
			double ask = this.history.getLastTick(instrument).getAsk();
			this.engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUY, getMinAmount(), ask, 5,
					ask - instrument.getPipValue() * SL_PIPS, ask + instrument.getPipValue() * TP_PIPS);
		}
	}

	private void openBuyPosition(IOrder order) throws JFException {
		if (this.trade) {
			Instrument instrument = order.getInstrument();
			double ask = this.history.getLastTick(instrument).getAsk();
			this.engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUY, order.getAmount(), ask, 5,
					order.getStopLossPrice(), order.getTakeProfitPrice());
		}
	}

	private void openSellPosition(Instrument instrument) throws JFException {
		if (this.trade) {
			double bid = this.history.getLastTick(instrument).getBid();
			this.engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELL, getMinAmount(), bid, 5,
					bid + instrument.getPipValue() * SL_PIPS, bid - instrument.getPipValue() * TP_PIPS);
		}
	}

	private void openSellPosition(IOrder order) throws JFException {
		if (this.trade) {
			Instrument instrument = order.getInstrument();
			double bid = this.history.getLastTick(instrument).getBid();
			this.engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELL, order.getAmount(), bid, 5,
					order.getStopLossPrice(), order.getTakeProfitPrice());
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		if (this.eurusdRenkos.size() > KIJUN_DAYS * 3) {
			this.eurusdRenkos.remove(0);
		}
		processTick(instrument, tick);
	}

	private void processTick(Instrument instrument, ITick tick) throws JFException {
		double price = side == OfferSide.BID ? tick.getBid() : tick.getAsk();
		double renkoHeight = instrument.getPipValue() * brickSize;
		double volume = side == OfferSide.BID ? tick.getBidVolume() : tick.getAskVolume();
		if (renko == null) {
			renko = new RenkoBar(price, volume, tick.getTime(), renkoHeight);
			return;
		}
		if (renko.high < price) {
			renko.high = price;
		}
		if (renko.low > price) {
			renko.low = price;
		}
		renko.close = price;
		renko.vol += volume;
		renko.endTime = tick.getTime();
		renko.tickCount++;

		if (renko.isComplete()) {
			renko.postProcess();
			process(renko, instrument);
			renko = RenkoBar.getNextRenko(renko);
		} else {
			myClose = false;
			closeWinningPositions(instrument);
		}
	}

	private void closeWinningPositions(Instrument instrument) throws JFException {
		List<IOrder> orders = this.engine.getOrders(instrument);
		for (IOrder order : orders) {
			if ((State.OPENED.equals(order.getState()) || State.FILLED.equals(order.getState()))) {
				if (order.getProfitLossInPips() >= CLOSE_TP_PIPS) {
					order.close();
					this.myClose = true;
				}
			}
		}
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (period.equals(Period.FOUR_HOURS)) {
			this.trade = !validateLastCandlestick(bidBar);
		}
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
//		LOGGER.info("ACCOUNT: {} ", message);
		if (Type.ORDER_FILL_OK.equals(message.getType())) {
			IOrder order = message.getOrder();
			if (order.isLong()) {
				if (order.getStopLossPrice() <= 0) {
					order.setStopLossPrice(order.getOpenPrice() - order.getInstrument().getPipValue() * (SL_PIPS + 2),
							OfferSide.BID, TS_PIPS);
				}
			} else {
				if (order.getStopLossPrice() <= 0) {
					order.setStopLossPrice(order.getOpenPrice() + order.getInstrument().getPipValue() * (SL_PIPS + 2),
							OfferSide.ASK, TS_PIPS);
				}
			}
		} else if (Type.ORDER_CLOSE_OK.equals(message.getType())) {
			if (this.myClose) {
				IOrder order = message.getOrder();
				processClose(order);
			}
		}
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onStop() throws JFException {
	}
}

class RenkoBar implements IRenkoBar {

	public double open;
	public double close;
	public double low;
	public double high;
	public double vol;
	public long startTime;
	public long endTime;
	public int tickCount;

	private RenkoBar prevRenko;
	private double height;

	public static RenkoBar getNextRenko(RenkoBar prevRenko) {
		RenkoBar renko = new RenkoBar(prevRenko.close, 0, prevRenko.endTime + 1, prevRenko.height);
		renko.prevRenko = prevRenko;
		return renko;
	}

	public RenkoBar(double price, double volume, long time, double height) {
		this.height = height;
		open = close = low = high = getRoundedPrice(price);
		vol = volume;
		startTime = time;
		endTime = time;
		tickCount = 1;
	}

	public boolean isComplete() {
		return isGreenComplete() || isRedComplete();
	}

	private boolean isGreenComplete() {
		return prevRenko == null ? high - open >= height : high - prevRenko.high >= height;
	}

	private boolean isRedComplete() {
		return prevRenko == null ? open - low >= height : prevRenko.low - low >= height;
	}

	public void postProcess() {
		if (isGreenComplete()) {
			low = high - height;
		} else {
			high = low + height;
		}
		low = getRoundedPrice(low);
		high = getRoundedPrice(high);
		close = getRoundedPrice(close);
		open = getRoundedPrice(open);
	}

	private double getRoundedPrice(double price) {
		double delta1 = price % height;
		double delta2 = height - price % height;
		return delta1 <= delta2 ? price - delta1 : price + delta2;
	}

	@Override
	public double getOpen() {
		return open;
	}

	@Override
	public double getClose() {
		return close;
	}

	@Override
	public double getLow() {
		return low;
	}

	@Override
	public double getHigh() {
		return high;
	}

	@Override
	public double getVolume() {
		return vol;
	}

	@Override
	public long getTime() {
		return startTime;
	}

	@Override
	public String toString() {
		return super.toString();
	}

	@Override
	public long getEndTime() {
		return endTime;
	}

	@Override
	public long getFormedElementsCount() {
		return tickCount;
	}

	@Override
	public IRenkoBar getInProgressBar() {
		return null;
	}

	@Override
	public Double getWickPrice() {
		return null;
	}

}