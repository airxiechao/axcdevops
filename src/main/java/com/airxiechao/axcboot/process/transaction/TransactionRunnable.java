package com.airxiechao.axcboot.process.transaction;

import org.slf4j.Logger;

import java.util.Map;

public interface TransactionRunnable {

    void run(Map stepStore, Map tranStore, Logger tlog) throws Exception;
}
