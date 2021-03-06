package org.recap.service.accession;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.marc4j.marc.Record;
import org.recap.RecapConstants;
import org.recap.converter.MarcToBibEntityConverter;
import org.recap.converter.SCSBToBibEntityConverter;
import org.recap.converter.XmlToBibEntityConverterInterface;
import org.recap.model.accession.AccessionRequest;
import org.recap.model.jaxb.BibRecord;
import org.recap.model.jaxb.JAXBHandler;
import org.recap.model.jaxb.marc.BibRecords;
import org.recap.model.jpa.*;
import org.recap.repository.jpa.*;
import org.recap.service.partnerservice.NYPLService;
import org.recap.service.partnerservice.PrincetonService;
import org.recap.util.MarcUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Created by chenchulakshmig on 20/10/16.
 */
@Service
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AccessionService {

    Logger log = Logger.getLogger(AccessionService.class);

    @Autowired
    MarcToBibEntityConverter marcToBibEntityConverter;

    @Autowired
    SCSBToBibEntityConverter scsbToBibEntityConverter;

    @Autowired
    CustomerCodeDetailsRepository customerCodeDetailsRepository;

    @Autowired
    ReportDetailRepository reportDetailRepository;

    @Autowired
    BibliographicDetailsRepository bibliographicDetailsRepository;

    @Autowired
    InstitutionDetailsRepository institutionDetailsRepository;

    @Autowired
    ItemDetailsRepository itemDetailsRepository;

    @Autowired
    ItemStatusDetailsRepository itemStatusDetailsRepository;

    @Autowired
    CollectionGroupDetailsRepository collectionGroupDetailsRepository;

    @Autowired
    PrincetonService princetonService;

    @Autowired
    NYPLService nyplService;

    @Autowired
    MarcUtil marcUtil;

    @Autowired
    SolrIndexService solrIndexService;

    @PersistenceContext
    private EntityManager entityManager;

    private Map<String,Integer> institutionEntityMap;

    private Map<String,Integer> itemStatusMap;

    private Map<String,Integer> collectionGroupMap;

    public MarcUtil getMarcUtil() {
        return marcUtil;
    }

    public MarcToBibEntityConverter getMarcToBibEntityConverter() {
        return marcToBibEntityConverter;
    }

    public SCSBToBibEntityConverter getScsbToBibEntityConverter() {
        return scsbToBibEntityConverter;
    }

    public ReportDetailRepository getReportDetailRepository() {
        return reportDetailRepository;
    }

    public SolrIndexService getSolrIndexService() {
        return solrIndexService;
    }

    public CustomerCodeDetailsRepository getCustomerCodeDetailsRepository() {
        return customerCodeDetailsRepository;
    }

    public BibliographicDetailsRepository getBibliographicDetailsRepository() {
        return bibliographicDetailsRepository;
    }

    public InstitutionDetailsRepository getInstitutionDetailsRepository() {
        return institutionDetailsRepository;
    }

    public PrincetonService getPrincetonService() {
        return princetonService;
    }

    public void setPrincetonService(PrincetonService princetonService) {
        this.princetonService = princetonService;
    }

    public NYPLService getNyplService() {
        return nyplService;
    }

    public void setNyplService(NYPLService nyplService) {
        this.nyplService = nyplService;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public Logger getLog() {
        return log;
    }

    public String getOwningInstitution(String customerCode) {
        String owningInstitution = null;
        try {
            CustomerCodeEntity customerCodeEntity = customerCodeDetailsRepository.findByCustomerCode(customerCode);
            if (null != customerCodeEntity) {
                owningInstitution = customerCodeEntity.getInstitutionEntity().getInstitutionCode();
            }
        } catch (Exception e) {
            log.error("Exception " + e);
        }
        return owningInstitution;
    }

    @Transactional
    public String processRequest(List<AccessionRequest> accessionRequestList) {
        String response = null;
        String bibDataResponse = null;
        List<Map<String, String>> responseMapList = new ArrayList<>();
        String owningInstitution = null;
        String customerCode = null;
        Set<String> messageSet = new HashSet<>();
        List<ReportDataEntity> reportDataEntityList = new ArrayList<>();
        for (AccessionRequest accessionRequest : accessionRequestList) {
            owningInstitution = getOwningInstitution(accessionRequest.getCustomerCode());
            if (owningInstitution == null) {
                messageSet.add(RecapConstants.CUSTOMER_CODE_DOESNOT_EXIST);
                reportDataEntityList.addAll(createReportDataEntityList(accessionRequest, RecapConstants.CUSTOMER_CODE_DOESNOT_EXIST));
            } else {
                try {
                    customerCode = accessionRequest.getCustomerCode();
                    if (owningInstitution != null && owningInstitution.equalsIgnoreCase(RecapConstants.PRINCETON)) {
                        bibDataResponse = getPrincetonService().getBibData(accessionRequest.getItemBarcode());
                        List<Record> records = new ArrayList<>();
                        if (StringUtils.isNotBlank(bibDataResponse)) {
                            records = getMarcUtil().readMarcXml(bibDataResponse);
                        }
                        if (CollectionUtils.isNotEmpty(records)) {
                            for (Record record : records) {
                                response = updateData(record, owningInstitution, responseMapList);
                                messageSet.add(response);
                                reportDataEntityList.addAll(createReportDataEntityList(accessionRequest, response));
                            }
                        }
                    } else if (owningInstitution != null && owningInstitution.equalsIgnoreCase(RecapConstants.NYPL)) {
                        bibDataResponse = nyplService.getBibData(accessionRequest.getItemBarcode(), customerCode);
                        BibRecords bibRecords = (BibRecords) JAXBHandler.getInstance().unmarshal(bibDataResponse, BibRecords.class);
                        for (BibRecord bibRecord : bibRecords.getBibRecords()) {
                            response = updateData(bibRecord, owningInstitution, responseMapList);
                            messageSet.add(response);
                            reportDataEntityList.addAll(createReportDataEntityList(accessionRequest, response));
                        }
                    }
                } catch (Exception ex) {
                    response = ex.getMessage();
                    reportDataEntityList.addAll(createReportDataEntityList(accessionRequest, response));
                }
                //Create dummy record
                response = createDummyRecordIfAny(response, owningInstitution, customerCode, messageSet, reportDataEntityList, accessionRequest);
                generateAccessionSummaryReport(responseMapList, owningInstitution);
            }
        }
        saveReportEntity(owningInstitution, reportDataEntityList);
        response = getResponseMessage(messageSet);
        return response;
    }

    private String createDummyRecordIfAny(String response, String owningInstitution, String customerCode, Set<String> messageSet, List<ReportDataEntity> reportDataEntityList, AccessionRequest accessionRequest) {
        if (response != null && response.equals(RecapConstants.ITEM_BARCODE_NOT_FOUND_MSG)) {
            BibliographicEntity fetchBibliographicEntity = getBibEntityUsingBarcodeForIncompleteRecord(accessionRequest.getItemBarcode());
            if (fetchBibliographicEntity == null) {
                response = createDummyRecord(accessionRequest.getItemBarcode(), customerCode, owningInstitution);
                messageSet.add(response);
                reportDataEntityList.addAll(createReportDataEntityList(accessionRequest, response));
            } else {
                response = RecapConstants.ITEM_BARCODE_ALREADY_ACCESSIONED_MSG;
                messageSet.add(response);
                reportDataEntityList.addAll(createReportDataEntityList(accessionRequest, response));
            }
        }
        return response;
    }

    private void saveReportEntity(String owningInstitution, List<ReportDataEntity> reportDataEntityList) {
        ReportEntity reportEntity;
        reportEntity = getReportEntity(owningInstitution!=null ? owningInstitution : RecapConstants.PRINCETON);
        reportEntity.setReportDataEntities(reportDataEntityList);
        reportDetailRepository.save(reportEntity);
    }

    private String getResponseMessage(Set<String> messageSet){
        Iterator<String> iterator = messageSet.iterator();
        StringBuffer responseMessageBuffer = new StringBuffer();
        String message = null;
        int count = 0;
        while(iterator.hasNext()){
            message = iterator.next();
            if(count>0){
                responseMessageBuffer.append(", ");
            }
            if (message.equals(RecapConstants.SUCCESS)) {
                responseMessageBuffer.append(RecapConstants.ACCESSION_SUCCESS);
            } else {
                responseMessageBuffer.append(message);
            }
            count++;
        }
        responseMessageBuffer.append(RecapConstants.VERIFY_ONGOING_ACCESSION_REPORT_MSG);
        return responseMessageBuffer.toString();
    }

    private ReportEntity getReportEntity(String owningInstitution){
        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(RecapConstants.ACCESSION_REPORT);
        reportEntity.setType(RecapConstants.ONGOING_ACCESSION_REPORT);
        reportEntity.setInstitutionName(owningInstitution);
        reportEntity.setCreatedDate(new Date());
        return reportEntity;
    }

    private List<ReportDataEntity> createReportDataEntityList(AccessionRequest accessionRequest,String response){
        List<ReportDataEntity> reportDataEntityList = new ArrayList<>();
        ReportDataEntity reportDataEntityCustomerCode = new ReportDataEntity();
        reportDataEntityCustomerCode.setHeaderName(RecapConstants.CUSTOMER_CODE);
        reportDataEntityCustomerCode.setHeaderValue(accessionRequest.getCustomerCode());
        reportDataEntityList.add(reportDataEntityCustomerCode);
        ReportDataEntity reportDataEntityItemBarcode = new ReportDataEntity();
        reportDataEntityItemBarcode.setHeaderName(RecapConstants.ITEM_BARCODE);
        reportDataEntityItemBarcode.setHeaderValue(accessionRequest.getItemBarcode());
        reportDataEntityList.add(reportDataEntityItemBarcode);
        ReportDataEntity reportDataEntityMessage = new ReportDataEntity();
        reportDataEntityMessage.setHeaderName(RecapConstants.MESSAGE);
        reportDataEntityMessage.setHeaderValue(response);
        reportDataEntityList.add(reportDataEntityMessage);
        return reportDataEntityList;
    }

    private String createDummyRecord(String itemBarcode, String customerCode, String owningInstitution) {
        String response;
        Integer owningInstitutionId = (Integer) getInstitutionEntityMap().get(owningInstitution);
        BibliographicEntity dummyBibliographicEntity = createDummyDataAsIncomplete(owningInstitutionId,itemBarcode,customerCode);
        solrIndexService.indexByBibliographicId(dummyBibliographicEntity.getBibliographicId());
        response = RecapConstants.ACCESSION_DUMMY_RECORD;
        return response;
    }

    private String updateData(Object record, String owningInstitution, List<Map<String,String>> responseMapList){
        String response = null;
        Map responseMap = getConverter(owningInstitution).convert(record, owningInstitution);
        responseMapList.add(responseMap);
        BibliographicEntity bibliographicEntity = (BibliographicEntity) responseMap.get(RecapConstants.BIBLIOGRAPHICENTITY);
        List<ReportEntity> reportEntityList = (List<ReportEntity>) responseMap.get(RecapConstants.REPORTENTITIES);
        if (CollectionUtils.isNotEmpty(reportEntityList)) {
            reportDetailRepository.save(reportEntityList);
        }
        if (bibliographicEntity != null) {
            BibliographicEntity savedBibliographicEntity = updateBibliographicEntity(bibliographicEntity);
            if (null != savedBibliographicEntity) {
                getSolrIndexService().indexByBibliographicId(savedBibliographicEntity.getBibliographicId());
                response = RecapConstants.SUCCESS;
            }
        }
        return response;
    }

    private BibliographicEntity createDummyDataAsIncomplete(Integer owningInstitutionId,String itemBarcode,String customerCode) {
        Random random = new Random();
        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        Date currentDate = new Date();
        try {
            bibliographicEntity.setContent(getXmlContent(RecapConstants.DUMMY_BIB_CONTENT_XML).getBytes());
            bibliographicEntity.setCreatedDate(currentDate);
            bibliographicEntity.setCreatedBy(RecapConstants.ACCESSION);
            bibliographicEntity.setLastUpdatedBy(RecapConstants.ACCESSION);
            bibliographicEntity.setLastUpdatedDate(currentDate);
            bibliographicEntity.setOwningInstitutionBibId(String.valueOf(random.nextInt()));
            bibliographicEntity.setOwningInstitutionId(owningInstitutionId);
            bibliographicEntity.setCatalogingStatus(RecapConstants.INCOMPLETE_STATUS);

            HoldingsEntity holdingsEntity = new HoldingsEntity();
            holdingsEntity.setContent(getXmlContent(RecapConstants.DUMMY_HOLDING_CONTENT_XML).getBytes());
            holdingsEntity.setCreatedDate(currentDate);
            holdingsEntity.setCreatedBy(RecapConstants.ACCESSION);
            holdingsEntity.setLastUpdatedDate(currentDate);
            holdingsEntity.setOwningInstitutionId(owningInstitutionId);
            holdingsEntity.setLastUpdatedBy(RecapConstants.ACCESSION);

            holdingsEntity.setOwningInstitutionHoldingsId(String.valueOf(random.nextInt()));

            ItemEntity itemEntity = new ItemEntity();
            itemEntity.setCallNumberType(RecapConstants.DUMMY_CALL_NUMBER);
            itemEntity.setCallNumber(RecapConstants.DUMMYCALLNUMBER);
            itemEntity.setCreatedDate(currentDate);
            itemEntity.setCreatedBy(RecapConstants.ACCESSION);
            itemEntity.setLastUpdatedDate(currentDate);
            itemEntity.setLastUpdatedBy(RecapConstants.ACCESSION);
            itemEntity.setBarcode(itemBarcode);
            itemEntity.setOwningInstitutionItemId(String.valueOf(random.nextInt()));
            itemEntity.setOwningInstitutionId(owningInstitutionId);
            itemEntity.setCollectionGroupId((Integer) getCollectionGroupMap().get(RecapConstants.SHARED_CGD));
            itemEntity.setCustomerCode(customerCode);
            itemEntity.setItemAvailabilityStatusId((Integer) getItemStatusMap().get(RecapConstants.NOT_AVAILABLE));
            itemEntity.setDeleted(false);
            itemEntity.setHoldingsEntities(Arrays.asList(holdingsEntity));
            itemEntity.setCatalogingStatus(RecapConstants.INCOMPLETE_STATUS);
            List<ItemEntity> itemEntityList = new ArrayList<>();
            itemEntityList.add(itemEntity);
            holdingsEntity.setItemEntities(itemEntityList);

            bibliographicEntity.setHoldingsEntities(Arrays.asList(holdingsEntity));
            bibliographicEntity.setItemEntities(Arrays.asList(itemEntity));
        } catch (Exception e) {
            e.printStackTrace();
        }

        BibliographicEntity savedBibliographicEntity = bibliographicDetailsRepository.saveAndFlush(bibliographicEntity);
        entityManager.refresh(savedBibliographicEntity);
        return savedBibliographicEntity;
    }

    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }

    private void generateAccessionSummaryReport(List<Map<String,String>> responseMapList,String owningInstitution){
        int successBibCount = 0;
        int successItemCount = 0;
        int failedBibCount = 0;
        int failedItemCount = 0;
        int exitsBibCount = 0;
        String reasonForFailureBib = "";
        String reasonForFailureItem = "";
        String itemBarcode = "";

        for(Map responseMap : responseMapList){
            successBibCount = successBibCount + (responseMap.get(RecapConstants.SUCCESS_BIB_COUNT)!=null ? (Integer) responseMap.get(RecapConstants.SUCCESS_BIB_COUNT) : 0);
            failedBibCount = failedBibCount + (responseMap.get(RecapConstants.FAILED_BIB_COUNT)!=null ? (Integer) responseMap.get(RecapConstants.FAILED_BIB_COUNT) : 0);
            if(failedBibCount == 0){
                if(StringUtils.isEmpty((String)responseMap.get(RecapConstants.REASON_FOR_ITEM_FAILURE))){
                    successItemCount = 1;
                }else{
                    failedItemCount = 1;
                }
            }
            exitsBibCount = exitsBibCount + (responseMap.get(RecapConstants.EXIST_BIB_COUNT)!=null ? (Integer) responseMap.get(RecapConstants.EXIST_BIB_COUNT) : 0);

            if(null != responseMap.get(RecapConstants.ITEMBARCODE) && !StringUtils.isEmpty(responseMap.get(RecapConstants.ITEMBARCODE).toString())){
                itemBarcode = responseMap.get(RecapConstants.ITEMBARCODE).toString();
            }
            if(!StringUtils.isEmpty((String)responseMap.get(RecapConstants.REASON_FOR_BIB_FAILURE))){
                if(!reasonForFailureBib.contains(responseMap.get(RecapConstants.REASON_FOR_BIB_FAILURE).toString())){
                    reasonForFailureBib =  responseMap.get(RecapConstants.REASON_FOR_BIB_FAILURE).toString()+ "," +reasonForFailureBib;
                }
            }
            if((!StringUtils.isEmpty((String)responseMap.get(RecapConstants.REASON_FOR_ITEM_FAILURE))) && StringUtils.isEmpty(reasonForFailureBib)){
                if(!reasonForFailureItem.contains((String)responseMap.get(RecapConstants.REASON_FOR_ITEM_FAILURE))){
                    reasonForFailureItem = (String)responseMap.get(RecapConstants.REASON_FOR_ITEM_FAILURE)+ "," +reasonForFailureItem;
                }
            }
        }

        List<ReportEntity> reportEntityList = new ArrayList<>();
        List<ReportDataEntity> reportDataEntities = new ArrayList<>();
        ReportEntity reportEntity = new ReportEntity();
        reportEntity.setFileName(RecapConstants.ACCESSION_REPORT);
        reportEntity.setType(RecapConstants.ACCESSION_SUMMARY_REPORT);
        reportEntity.setCreatedDate(new Date());
        reportEntity.setInstitutionName(owningInstitution);

        ReportDataEntity successBibCountReportDataEntity = new ReportDataEntity();
        successBibCountReportDataEntity.setHeaderName(RecapConstants.BIB_SUCCESS_COUNT);
        successBibCountReportDataEntity.setHeaderValue(String.valueOf(successBibCount));
        reportDataEntities.add(successBibCountReportDataEntity);

        ReportDataEntity successItemCountReportDataEntity = new ReportDataEntity();
        successItemCountReportDataEntity.setHeaderName(RecapConstants.ITEM_SUCCESS_COUNT);
        successItemCountReportDataEntity.setHeaderValue(String.valueOf(successItemCount));
        reportDataEntities.add(successItemCountReportDataEntity);

        ReportDataEntity existsBibCountReportDataEntity = new ReportDataEntity();
        existsBibCountReportDataEntity.setHeaderName(RecapConstants.NUMBER_OF_BIB_MATCHES);
        existsBibCountReportDataEntity.setHeaderValue(String.valueOf(exitsBibCount));
        reportDataEntities.add(existsBibCountReportDataEntity);

        ReportDataEntity failedBibCountReportDataEntity = new ReportDataEntity();
        failedBibCountReportDataEntity.setHeaderName(RecapConstants.BIB_FAILURE_COUNT);
        failedBibCountReportDataEntity.setHeaderValue(String.valueOf(failedBibCount));
        reportDataEntities.add(failedBibCountReportDataEntity);

        ReportDataEntity failedItemCountReportDataEntity = new ReportDataEntity();
        failedItemCountReportDataEntity.setHeaderName(RecapConstants.ITEM_FAILURE_COUNT);
        failedItemCountReportDataEntity.setHeaderValue(String.valueOf(failedItemCount));
        reportDataEntities.add(failedItemCountReportDataEntity);

        ReportDataEntity reasonForBibFailureReportDataEntity = new ReportDataEntity();
        reasonForBibFailureReportDataEntity.setHeaderName(RecapConstants.FAILURE_BIB_REASON);
        if(reasonForFailureBib.startsWith("\n")){
            reasonForFailureBib = reasonForFailureBib.substring(1,reasonForFailureBib.length()-1);
        }
        reasonForFailureBib = reasonForFailureBib.replaceAll("\n",",");
        reasonForFailureBib = reasonForFailureBib.replaceAll(",$", "");
        reasonForBibFailureReportDataEntity.setHeaderValue(reasonForFailureBib);
        reportDataEntities.add(reasonForBibFailureReportDataEntity);

        ReportDataEntity reasonForItemFailureReportDataEntity = new ReportDataEntity();
        reasonForItemFailureReportDataEntity.setHeaderName(RecapConstants.FAILURE_ITEM_REASON);
        if(reasonForFailureItem.startsWith("\n")){
            reasonForFailureItem = reasonForFailureItem.substring(1,reasonForFailureItem.length()-1);
        }
        reasonForFailureItem = reasonForFailureItem.replaceAll("\n",",");
        reasonForFailureItem = reasonForFailureItem.replaceAll(",$", "");
        reasonForItemFailureReportDataEntity.setHeaderValue(reasonForFailureItem);
        reportDataEntities.add(reasonForItemFailureReportDataEntity);

        reportEntity.setReportDataEntities(reportDataEntities);
        reportEntityList.add(reportEntity);
        getReportDetailRepository().save(reportEntityList);
    }

    public BibliographicEntity updateBibliographicEntity(BibliographicEntity bibliographicEntity) {
        BibliographicEntity savedBibliographicEntity=null;
        BibliographicEntity fetchBibliographicEntity = getBibliographicDetailsRepository().findByOwningInstitutionIdAndOwningInstitutionBibId(bibliographicEntity.getOwningInstitutionId(),bibliographicEntity.getOwningInstitutionBibId());
        if(fetchBibliographicEntity ==null) { // New Bib Record

            savedBibliographicEntity = getBibliographicDetailsRepository().saveAndFlush(bibliographicEntity);
            getEntityManager().refresh(savedBibliographicEntity);
        }else{ // Existing bib Record
            // Bib
            fetchBibliographicEntity.setContent(bibliographicEntity.getContent());
            fetchBibliographicEntity.setCreatedBy(bibliographicEntity.getCreatedBy());
            fetchBibliographicEntity.setCreatedDate(bibliographicEntity.getCreatedDate());
            fetchBibliographicEntity.setDeleted(bibliographicEntity.isDeleted());
            fetchBibliographicEntity.setLastUpdatedBy(bibliographicEntity.getLastUpdatedBy());
            fetchBibliographicEntity.setLastUpdatedDate(bibliographicEntity.getLastUpdatedDate());

            // Holding
            List<HoldingsEntity> fetchHoldingsEntities =fetchBibliographicEntity.getHoldingsEntities();
            List<HoldingsEntity> holdingsEntities = new ArrayList<>(bibliographicEntity.getHoldingsEntities());

            log.info("fetchHoldingsEntities = "+fetchHoldingsEntities.size());
            log.info("holdingsEntities = "+holdingsEntities.size());

            for (Iterator iholdings = holdingsEntities.iterator(); iholdings.hasNext();) {
                HoldingsEntity holdingsEntity =(HoldingsEntity) iholdings.next();
                for (int j=0;j<fetchHoldingsEntities.size();j++) {
                    HoldingsEntity fetchHolding=fetchHoldingsEntities.get(j);
                    if(fetchHolding.getOwningInstitutionHoldingsId().equalsIgnoreCase(holdingsEntity.getOwningInstitutionHoldingsId())  && fetchHolding.getOwningInstitutionId().intValue() == holdingsEntity.getOwningInstitutionId().intValue()) {
                        copyHoldingsEntity(fetchHolding,holdingsEntity);
                        iholdings.remove();
                    }else{
                        List<ItemEntity> fetchedItemEntityList = fetchHolding.getItemEntities();
                        List<ItemEntity> itemEntityList = holdingsEntity.getItemEntities();
                        for(ItemEntity fetchedItemEntity : fetchedItemEntityList){
                            for(ItemEntity itemEntity : itemEntityList){
                                if(fetchedItemEntity.getOwningInstitutionItemId().equals(itemEntity.getOwningInstitutionItemId())){
                                    copyHoldingsEntity(fetchHolding,holdingsEntity);
                                    iholdings.remove();
                                }
                            }
                        }

                    }
                }
            }
            fetchHoldingsEntities.addAll(holdingsEntities);
            log.info("Holding Final Count = "+fetchHoldingsEntities.size());

            // Item
            List<ItemEntity> fetchItemsEntities =fetchBibliographicEntity.getItemEntities();
            List<ItemEntity> itemsEntities = new ArrayList<>(bibliographicEntity.getItemEntities());

            log.info("fetchHoldingsEntities = "+fetchItemsEntities.size());
            log.info("holdingsEntities = "+itemsEntities.size());

            for (Iterator iItems=itemsEntities.iterator();iItems.hasNext();) {
                ItemEntity itemEntity =(ItemEntity) iItems.next();
                for (Iterator ifetchItems=fetchItemsEntities.iterator();ifetchItems.hasNext();) {
                    ItemEntity fetchItem=(ItemEntity) ifetchItems.next();
                    if(fetchItem.getOwningInstitutionItemId().equalsIgnoreCase(itemEntity.getOwningInstitutionItemId())  && fetchItem.getOwningInstitutionId().intValue() == itemEntity.getOwningInstitutionId().intValue()) {
                        copyItemEntity(fetchItem,itemEntity);
                        iItems.remove();
                    }
                }
            }
            fetchItemsEntities.addAll(itemsEntities);
            log.info("Item Final Count = "+fetchItemsEntities.size());

            fetchBibliographicEntity.setHoldingsEntities(fetchHoldingsEntities);
            fetchBibliographicEntity.setItemEntities(fetchItemsEntities);

            savedBibliographicEntity = getBibliographicDetailsRepository().saveAndFlush(fetchBibliographicEntity);
            getEntityManager().refresh(savedBibliographicEntity);
        }
        return savedBibliographicEntity;
    }

    private BibliographicEntity getBibEntityUsingBarcodeForIncompleteRecord(String itemBarcode){
        List<String> itemBarcodeList = new ArrayList<>();
        itemBarcodeList.add(itemBarcode);
        List<ItemEntity> itemEntityList = itemDetailsRepository.findByBarcodeIn(itemBarcodeList);
        BibliographicEntity fetchedBibliographicEntity = null;
        if(itemEntityList != null && itemEntityList.size() > 0 && itemEntityList.get(0).getBibliographicEntities() != null){
            fetchedBibliographicEntity = itemEntityList.get(0).getBibliographicEntities().get(0);
        }
        return fetchedBibliographicEntity;
    }

    private HoldingsEntity copyHoldingsEntity(HoldingsEntity fetchHoldingsEntity, HoldingsEntity holdingsEntity){
        fetchHoldingsEntity.setContent(holdingsEntity.getContent());
        fetchHoldingsEntity.setCreatedBy(holdingsEntity.getCreatedBy());
        fetchHoldingsEntity.setCreatedDate(holdingsEntity.getCreatedDate());
        fetchHoldingsEntity.setDeleted(holdingsEntity.isDeleted());
        fetchHoldingsEntity.setLastUpdatedBy(holdingsEntity.getLastUpdatedBy());
        fetchHoldingsEntity.setLastUpdatedDate(holdingsEntity.getLastUpdatedDate());
        return fetchHoldingsEntity;
    }

    private ItemEntity copyItemEntity(ItemEntity fetchItemEntity, ItemEntity itemEntity){
        fetchItemEntity.setBarcode(itemEntity.getBarcode());
        fetchItemEntity.setCreatedBy(itemEntity.getCreatedBy());
        fetchItemEntity.setCreatedDate(itemEntity.getCreatedDate());
        fetchItemEntity.setDeleted(itemEntity.isDeleted());
        fetchItemEntity.setLastUpdatedBy(itemEntity.getLastUpdatedBy());
        fetchItemEntity.setLastUpdatedDate(itemEntity.getLastUpdatedDate());
        fetchItemEntity.setCallNumber(itemEntity.getCallNumber());
        fetchItemEntity.setCustomerCode(itemEntity.getCustomerCode());
        fetchItemEntity.setCallNumberType(itemEntity.getCallNumberType());
        fetchItemEntity.setItemAvailabilityStatusId(itemEntity.getItemAvailabilityStatusId());
        fetchItemEntity.setCopyNumber(itemEntity.getCopyNumber());
        fetchItemEntity.setCollectionGroupId(itemEntity.getCollectionGroupId());
        fetchItemEntity.setUseRestrictions(itemEntity.getUseRestrictions());
        fetchItemEntity.setVolumePartYear(itemEntity.getVolumePartYear());
        return fetchItemEntity;
    }

    private XmlToBibEntityConverterInterface getConverter(String institutionId){
        if(institutionId.equalsIgnoreCase(RecapConstants.PRINCETON)){
            return getMarcToBibEntityConverter();
        } else if(institutionId.equalsIgnoreCase(RecapConstants.NYPL)){
            return getScsbToBibEntityConverter();
        }
        return null;
    }

    public Map getInstitutionEntityMap() {
        if (null == institutionEntityMap) {
            institutionEntityMap = new HashMap();
            try {
                Iterable<InstitutionEntity> institutionEntities = institutionDetailsRepository.findAll();
                for (Iterator iterator = institutionEntities.iterator(); iterator.hasNext(); ) {
                    InstitutionEntity institutionEntity = (InstitutionEntity) iterator.next();
                    institutionEntityMap.put( institutionEntity.getInstitutionCode(),institutionEntity.getInstitutionId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return institutionEntityMap;
    }

    public Map getItemStatusMap() {
        if (null == itemStatusMap) {
            itemStatusMap = new HashMap();
            try {
                Iterable<ItemStatusEntity> itemStatusEntities = itemStatusDetailsRepository.findAll();
                for (Iterator iterator = itemStatusEntities.iterator(); iterator.hasNext(); ) {
                    ItemStatusEntity itemStatusEntity = (ItemStatusEntity) iterator.next();
                    itemStatusMap.put(itemStatusEntity.getStatusCode(), itemStatusEntity.getItemStatusId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return itemStatusMap;
    }

    public Map getCollectionGroupMap() {
        if (null == collectionGroupMap) {
            collectionGroupMap = new HashMap();
            try {
                Iterable<CollectionGroupEntity> collectionGroupEntities = collectionGroupDetailsRepository.findAll();
                for (Iterator iterator = collectionGroupEntities.iterator(); iterator.hasNext(); ) {
                    CollectionGroupEntity collectionGroupEntity = (CollectionGroupEntity) iterator.next();
                    collectionGroupMap.put(collectionGroupEntity.getCollectionGroupCode(), collectionGroupEntity.getCollectionGroupId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return collectionGroupMap;
    }

    private String getXmlContent(String filename) {
        InputStream inputStream = getClass().getResourceAsStream(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder out = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    out.append("\n");
                } else {
                    out.append(line);
                    out.append("\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toString();
    }
}
