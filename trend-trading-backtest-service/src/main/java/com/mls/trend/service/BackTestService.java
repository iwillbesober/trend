package com.mls.trend.service;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.mls.trend.entity.IndexData;
import com.mls.trend.entity.Profit;
import com.mls.trend.entity.Trade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BackTestService {
    @Autowired
    IndexDataClient indexDataClient;

    public List<IndexData> listIndexData(String code){
        List<IndexData> result = indexDataClient.getIndexData(code);
        Collections.reverse(result);

//        for(IndexData indexData:result){
//            System.out.println(indexData.getDate());
//        }

        return result;
    }
    public Map<String,Object> simulate(int ma, float sellRate, float buyRate, float serviceCharge, List<IndexData> indexDatas)  {

        List<Profit> profits =new ArrayList<>();
        List<Trade> trades = new ArrayList<>();
        float initCash = 1000;          //本金
        float cash = initCash;         //当前手里的资金
        float share = 0;              //股票份额
        float value = 0;              //总资产


         /*
         * winCount：盈利次数
         * lossCount：亏损次数
         * avgWinRate：平均盈利率
         * avgLossRate：平均亏损率
         * totalWinRate：总交易盈利率
         * totalLossRate：总交易亏损率
         **/
        int winCount = 0;
        float totalWinRate = 0;
        float avgWinRate = 0;
        float totalLossRate = 0;
        int lossCount = 0;
        float avgLossRate = 0;

        float init =0;                //均线起点的收盘点
        if(!indexDatas.isEmpty())
            init = indexDatas.get(0).getClosePoint();

        for (int i = 0; i<indexDatas.size() ; i++) {
            IndexData indexData = indexDatas.get(i);
            float closePoint = indexData.getClosePoint();
            float avg = getMA(i,ma,indexDatas);         //均线
            float max = getMax(i,ma,indexDatas);        //最高点收盘价

            float increase_rate = closePoint/avg;      //增长率
            float decrease_rate = closePoint/max;      //下跌率

            if(avg!=0) {
                //buy 超过了均线
                if(increase_rate>buyRate  ) {
                    //如果没买，则进行购买
                    if(0 == share) {
                        share = cash / closePoint;
                        cash = 0;
                        Trade trade = new Trade();
                        trade.setBuyDate(indexData.getDate());
                        trade.setBuyClosePoint(indexData.getClosePoint());
                        trade.setSellDate("n/a");
                        trade.setSellClosePoint(0);
                        trades.add(trade);
                    }
                }
                //sell 低于了卖点
                else if(decrease_rate<sellRate ) {
                    //如果没卖，就卖掉所有份额
                    if(0!= share){
                        cash = closePoint * share * (1-serviceCharge);
                        share = 0;
                        Trade trade =trades.get(trades.size()-1);
                        trade.setSellDate(indexData.getDate());
                        trade.setSellClosePoint(indexData.getClosePoint());
                        float rate = cash / initCash;
                        trade.setRate(rate);

                        if(trade.getSellClosePoint()-trade.getBuyClosePoint()>0) {
                            totalWinRate +=(trade.getSellClosePoint()-trade.getBuyClosePoint())/trade.getBuyClosePoint();
                            winCount++;
                        }

                        else {
                            totalLossRate +=(trade.getSellClosePoint()-trade.getBuyClosePoint())/trade.getBuyClosePoint();
                            lossCount ++;
                        }
                    }
                }
                //do nothing
                else{

                }
            }

            if(share!=0) {
                value = closePoint * share;
            }
            else {
                value = cash;
            }
            float rate = value/initCash;

            Profit profit = new Profit();
            profit.setDate(indexData.getDate());
            profit.setValue(rate*init);

            System.out.println("profit.value:" + profit.getValue());
            profits.add(profit);

        }

        avgWinRate = totalWinRate / winCount;
        avgLossRate = totalLossRate / lossCount;

        Map<String,Object> map = new HashMap<>();
        map.put("profits", profits);
        map.put("trades", trades);
        map.put("winCount", winCount);
        map.put("lossCount", lossCount);
        map.put("avgWinRate", avgWinRate);
        map.put("avgLossRate", avgLossRate);
        return map;
    }


    //计算day天内最高收盘价
    private static float getMax(int i, int day, List<IndexData> list) {
        int start = i-day;   //均线计算日期的起点
        if(start<0) return 0;
        int now = i-1;          //均线计算日期的终点
        float max = 0;
        for (int j = start; j <=now; j++) {
            IndexData bean =list.get(j);
            if(bean.getClosePoint()>max) {
                max = bean.getClosePoint();
            }
        }
        return max;
    }

    //计算ma天内均值
    private static float getMA(int i, int ma, List<IndexData> list) {
        int start = i-ma;
        int now = i-1;

        if(start<0)
            return 0;

        float sum = 0;
        float avg = 0;
        for (int j = start; j <=now; j++) {
            IndexData bean =list.get(j);
            sum += bean.getClosePoint();
        }
        avg = sum / (now - start + 1);
        return avg;
    }

    //用于计算当前的时间范围是多少年
    public float getYear(List<IndexData> allIndexDatas) {
        float years;
        String sDateStart = allIndexDatas.get(0).getDate();
        String sDateEnd = allIndexDatas.get(allIndexDatas.size()-1).getDate();

        Date dateStart = DateUtil.parse(sDateStart);
        Date dateEnd = DateUtil.parse(sDateEnd);

        long days = DateUtil.between(dateStart, dateEnd, DateUnit.DAY);
        years = days/365f;
        return years;
    }

}
