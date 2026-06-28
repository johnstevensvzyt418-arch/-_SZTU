package cn.edu.sztu.elevatormonitor.utils;

import cn.edu.sztu.elevatormonitor.entity.Elevator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ExcelReader {
    public List<Elevator> read(String fileName) throws IOException {
        if (fileName == null) {
            return null;
        }
        FileInputStream is = new FileInputStream(fileName);
        Workbook workbook = new XSSFWorkbook(is);
        int numberOfSheets = workbook.getNumberOfSheets();
        if (numberOfSheets <= 0) {
            workbook.close();
            return null;
        }

        List<Elevator> list = new ArrayList<>();
        Sheet sheet = workbook.getSheetAt(0);
        int rowNum = sheet.getLastRowNum();

        for (int row = 1; row < rowNum; row++) {
            Row r = sheet.getRow(row);
            if (r.getPhysicalNumberOfCells() >= 2) {
                Elevator elevatorData = new Elevator(r.getCell(0).toString(), r.getCell(1).toString());
                list.add(elevatorData);
            }
        }
        workbook.close();
        return list;
    }
}
