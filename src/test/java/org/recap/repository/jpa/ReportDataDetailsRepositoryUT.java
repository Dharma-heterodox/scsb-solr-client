package org.recap.repository.jpa;

import org.junit.Test;
import org.recap.BaseTestCase;
import org.recap.RecapConstants;
import org.recap.model.jpa.ReportDataEntity;
import org.recap.model.jpa.ReportEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by angelind on 9/1/17.
 */
public class ReportDataDetailsRepositoryUT extends BaseTestCase{

    @Autowired
    ReportDetailRepository reportDetailRepository;

    @Autowired
    ReportDataDetailsRepository reportDataDetailsRepository;

    @Test
    public void getCountOfRecordNumForMatchingMonographTest() throws Exception {
        saveReportEntity();
        long countOfRecordNumForMatchingMonograph = reportDataDetailsRepository.getCountOfRecordNumForMatchingMonograph("BibId");
        assertTrue(countOfRecordNumForMatchingMonograph > 0);
    }

    @Test
    public void getReportDataEntityForMatchingMonographsTest() throws Exception {
        saveReportEntity();
        List<ReportDataEntity> reportDataEntities = reportDataDetailsRepository.getReportDataEntityForMatchingMonographs("BibId", 0, 100);
        assertNotNull(reportDataEntities);
    }

    private ReportEntity saveReportEntity() {
        List<ReportDataEntity> reportDataEntities = new ArrayList<>();

        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(RecapConstants.MATCHING_ALGO_FULL_FILE_NAME);
        reportEntity.setCreatedDate(new Date());
        reportEntity.setType("MultiMatch");
        reportEntity.setInstitutionName(RecapConstants.ALL_INST);

        ReportDataEntity reportDataEntity1 = new ReportDataEntity();
        reportDataEntity1.setHeaderName("BibId");
        reportDataEntity1.setHeaderValue("1,2");
        reportDataEntities.add(reportDataEntity1);

        ReportDataEntity reportDataEntity2 = new ReportDataEntity();
        reportDataEntity2.setHeaderName("MaterialType");
        reportDataEntity2.setHeaderValue("Monograph,Monograph");
        reportDataEntities.add(reportDataEntity2);

        reportEntity.setReportDataEntities(reportDataEntities);
        return reportDetailRepository.save(reportEntity);
    }

    @Test
    public void getRecordsForMatchingBibInfo(){
        List<String> recordNumList = new ArrayList<>();
        recordNumList.add("1");
        List<String> headerNameList = new ArrayList<>();
        headerNameList.add("BibId");
        headerNameList.add("OwningInstitution");
        headerNameList.add("OwningInstitutionBibId");
        List<ReportDataEntity> reportDataEntityList = reportDataDetailsRepository.getRecordsForMatchingBibInfo(recordNumList,headerNameList);
        assertNotNull(reportDataEntityList);
    }

}