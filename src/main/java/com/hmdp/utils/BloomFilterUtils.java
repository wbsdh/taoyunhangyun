package com.hmdp.utils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;


public class BloomFilterUtils {
    private static BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(
            Charset.forName("utf-8")),10000, 0.0001);;

    public static Boolean get(String key){
        return bloomFilter.mightContain(key);
    }

    public static Boolean put(String key){
        boolean put = bloomFilter.put(key);
        return put;
    }
}
