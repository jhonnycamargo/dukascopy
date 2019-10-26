/*
 * Copyright (c) 2017 Dukascopy (Suisse) SA. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 * 
 * Neither the name of Dukascopy (Suisse) SA or the names of contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. DUKASCOPY (SUISSE) SA ("DUKASCOPY")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL DUKASCOPY OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF DUKASCOPY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 */
package singlejartest;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.LoadingProgressListener;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.ITesterClient.DataLoadingMethod;
import com.dukascopy.api.system.TesterFactory;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy
 */
public class TesterMain {
	private static final Logger LOGGER = LoggerFactory.getLogger(TesterMain.class);

	private static String jnlpUrl = "http://platform.dukascopy.com/demo/jforex.jnlp";
	private static String userName = "userName"; // TODO change me
	private static String password = "Password"; // TODO change me
	private static ITesterClient client;
	private static String reportsFileLocation = "/home/merkhet/Documents/Forex/testDuka/report.html";

	public static void main(String[] args) throws Exception {
		client = TesterFactory.getDefaultInstance();
		final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		Date dateFrom = dateFormat.parse("07/01/2019 00:00:00");
		Date dateTo = dateFormat.parse("07/15/2019 00:00:00");

		client.setDataInterval(DataLoadingMethod.ALL_TICKS, dateFrom.getTime(), dateTo.getTime());

		setSystemListener();
		tryToConnect();
		subscribeToInstruments();
		client.setInitialDeposit(Instrument.EURUSD.getSecondaryJFCurrency(), 1000);
		loadData();

		LOGGER.info("Starting strategy");
		client.startStrategy(new RenkoCrossingKijunSenStrategy(), getLoadingProgressListener());
		// now it's running
	}

	private static void setSystemListener() {
		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				File reportFile = new File(reportsFileLocation);
				try {
					client.createReport(processId, reportFile);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
				if (client.getStartedStrategies().size() == 0) {
					System.exit(0);
				}
			}

			@Override
			public void onConnect() {
				LOGGER.info("Connected");
			}

			@Override
			public void onDisconnect() {
				// tester doesn't disconnect
			}
		});
	}

	private static void tryToConnect() throws Exception {
		LOGGER.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading
		client.connect(jnlpUrl, userName, password);

		// wait for it to connect
		int i = 10; // wait max ten seconds
		while (i > 0 && !client.isConnected()) {
			Thread.sleep(1000);
			i--;
		}
		if (!client.isConnected()) {
			LOGGER.error("Failed to connect Dukascopy servers");
			System.exit(1);
		}
	}

	private static void subscribeToInstruments() {
		// set instruments that will be used in testing
		Set<Instrument> instruments = new HashSet<>();
		instruments.add(Instrument.EURUSD);
//		instruments.add(Instrument.AUDNZD);
//		instruments.add(Instrument.GBPUSD);
//		instruments.add(Instrument.AUDUSD);
//		instruments.add(Instrument.USDCHF);
//		instruments.add(Instrument.NZDUSD);
//		instruments.add(Instrument.GBPCHF);
//		instruments.add(Instrument.EURAUD);
//		instruments.add(Instrument.GBPAUD);
//		instruments.add(Instrument.GBPNZD);
//		instruments.add(Instrument.EURNZD);
//		instruments.add(Instrument.EURGBP);
		LOGGER.info("Subscribing instruments...");
		client.setSubscribedInstruments(instruments);
	}

	private static void loadData() throws InterruptedException, java.util.concurrent.ExecutionException {
		// load data
		LOGGER.info("Downloading data");
		Future<?> future = client.downloadData(null);
		// wait for downloading to complete
		future.get();
	}

	private static LoadingProgressListener getLoadingProgressListener() {
		return new LoadingProgressListener() {
			@Override
			public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
				LOGGER.info(information);
			}

			@Override
			public void loadingFinished(boolean allDataLoaded, long startTime, long endTime, long currentTime) {
			}

			@Override
			public boolean stopJob() {
				return false;
			}
		};
	}
}
