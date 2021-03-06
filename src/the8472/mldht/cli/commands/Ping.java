package the8472.mldht.cli.commands;

import the8472.bencode.Utils;
import the8472.mldht.cli.CommandProcessor;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.RPCCall;
import lbms.plugins.mldht.kad.RPCCallListener;
import lbms.plugins.mldht.kad.RPCServer;
import lbms.plugins.mldht.kad.messages.MessageBase;
import lbms.plugins.mldht.kad.messages.PingRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Ping extends CommandProcessor {
	
	InetSocketAddress target;
	
	ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
	
	{
		timer.setKeepAliveTime(2, TimeUnit.SECONDS);
		timer.allowCoreThreadTimeOut(true);
	}
	
	AtomicInteger iteration = new AtomicInteger();
		
	
	@Override
	protected void process() {
		InetAddress addr;
		int port;
		
		try {
			String ip = arguments.get(0);
			port = Integer.valueOf(arguments.get(1));
			
			addr = InetAddress.getByName(ip);
		} catch (Exception e) {
			handleException(e);
			return;
		}
		
		target = new InetSocketAddress(addr, port);
		
		println("PING " + target);
		
		doPing();
	}
	
	void doPing() {
		if(!isRunning())
			return;
		
		Optional<DHT> dht = dhts.stream().filter(d -> d.getType().PREFERRED_ADDRESS_TYPE == target.getAddress().getClass()).findAny();
		
		
		if(!dht.isPresent()) {
			printErr("no dht with an address type matching " + target.getAddress() + " found");
			exit(1);
		}
		
		
		
		RPCServer srv = dht.get().getServerManager().getRandomActiveServer(true);
		PingRequest req = new PingRequest();
		req.setDestination(target);
		RPCCall call = new RPCCall(req);
		call.addListener(new RPCCallListener() {
			
			int counter = iteration.incrementAndGet();
			
			@Override
			public void onTimeout(RPCCall c) {
				println("#"+counter+": timed out");
				timer.schedule(Ping.this::doPing, 1, TimeUnit.SECONDS);
			}
			
			@Override
			public void onStall(RPCCall c) {}
			
			@Override
			public void onResponse(RPCCall c, MessageBase rsp) {
				println("#"+counter+" response time=" + c.getRTT() + "ms " + rsp.getID() + rsp.getVersion().map(v -> " ver:" + Utils.prettyPrint(v)).orElse(""));
				timer.schedule(Ping.this::doPing, 1, TimeUnit.SECONDS);
				
			}
		});
		srv.doCall(call);
	}

}
