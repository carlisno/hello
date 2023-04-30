package com.lkd.utils;

import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DateUtil{

    /**
     * 获取当前时间的季度信息
     * @param dateTime
     * @return
     */
    public static Season getSeason(LocalDateTime dateTime){
        int firstMonth = dateTime.getMonth().firstMonthOfQuarter().getValue();
        int lastMonth = firstMonth + 2;
        LocalDateTime start = LocalDateTime.of(dateTime.getYear(),firstMonth,1,0,0,0);
        Season s = new Season();
        s.setStartDate(start);
        LocalDateTime end = LocalDateTime.of(dateTime.getYear(),lastMonth,1,0,0,0);
        end = end.plusMonths(1).plusDays(-1);
        s.setEndDate(end);

        return s;
    }

    /**
     * 季节
     */
    @Data
    public static class Season{
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }




    /**
     * *************看我看我**************
     * 传入两个时间范围，返回这两个时间范围内的所有时间，并保存在一个集合中
     * @param beginTime
     * @param endTime
     * @return
     * @throws ParseException
     */
    public static List<String> findDates(String beginTime, String endTime)
            throws ParseException {
        List<String> allDate = new ArrayList();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Date dBegin = sdf.parse(beginTime);
        Date dEnd = sdf.parse(endTime);
        allDate.add(sdf.format(dBegin));
        Calendar calBegin = Calendar.getInstance();
        // 使用给定的 Date 设置此 Calendar 的时间
        calBegin.setTime(dBegin);
        Calendar calEnd = Calendar.getInstance();
        // 使用给定的 Date 设置此 Calendar 的时间
        calEnd.setTime(dEnd);
        // 测试此日期是否在指定日期之后
        while (dEnd.after(calBegin.getTime())) {
            // 根据日历的规则，为给定的日历字段添加或减去指定的时间量
            calBegin.add(Calendar.DAY_OF_MONTH, 1);
            allDate.add(sdf.format(calBegin.getTime()));
        }
        System.out.println("时间==" + allDate);
        return allDate;
    }

    public static List<String> findMonthDates(String beginTime, String endTime)
            throws ParseException {
        List<String> allDate = new ArrayList();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");

        Date dBegin = sdf.parse(beginTime);
        Date dEnd = sdf.parse(endTime);
        allDate.add(sdf.format(dBegin));
        Calendar calBegin = Calendar.getInstance();
        // 使用给定的 Date 设置此 Calendar 的时间
        calBegin.setTime(dBegin);
        Calendar calEnd = Calendar.getInstance();
        // 使用给定的 Date 设置此 Calendar 的时间
        calEnd.setTime(dEnd);
        // 测试此日期是否在指定日期之后
        while (dEnd.after(calBegin.getTime())) {
            // 根据日历的规则，为给定的日历字段添加或减去指定的时间量
//            calBegin.add(Calendar.DAY_OF_MONTH, 1);
            calBegin.add(Calendar.MONTH, 1);
            allDate.add(sdf.format(calBegin.getTime()));
        }
        System.out.println("时间==" + allDate);
        return allDate;
    }

    public static void main(String[] args) throws ParseException {
        List<String> dates = findDates("2022-12-26", "2023-01-02");
        System.out.println(dates);
        List<String> monthDates = findMonthDates("2022-12", "2023-01");
        System.out.println(monthDates);

        System.out.println(LocalDate.now().toString());
    }
}
