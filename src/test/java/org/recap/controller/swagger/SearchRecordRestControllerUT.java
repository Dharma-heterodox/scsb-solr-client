package org.recap.controller.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.recap.controller.BaseControllerUT;
import org.recap.model.search.SearchRecordsRequest;
import org.recap.model.search.SearchResultRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Created by premkb on 19/8/16.
 */
public class SearchRecordRestControllerUT extends BaseControllerUT {

    private static final Logger logger = LoggerFactory.getLogger(SearchRecordRestController.class);

    @Autowired
    private SearchRecordRestController searchRecordRestController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.mockMvc = MockMvcBuilders.standaloneSetup(searchRecordRestController).build();
    }

    @Test
    public void searchRestfulApiEmpty() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String paramString="sdaasdadad{}{[[[]]";
        MvcResult mvcResult = this.mockMvc.perform(post("/searchService/search")
                .param("requestJson",paramString)
                .contentType(contentType)
                .content(objectMapper.writeValueAsString(new SearchRecordsRequest())))
                .andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertTrue(status == 200);
    }

    @Test
    public void searchRestfulApi() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest();
        searchRecordsRequest.setOwningInstitutions(Arrays.asList("PUL"));
        searchRecordsRequest.setMaterialTypes(Arrays.asList("Other"));
        searchRecordsRequest.setUseRestrictions(Arrays.asList("NoRestrictions","InLibraryUse","SupervisedUse"));
        searchRecordsRequest.setPageSize(10);
        String paramString="{\"owningInstitutions\" : [\"PUL\" ],\"materialTypes\" : [ \"Other\" ],\"useRestrictions\" : [\"NoRestrictions\" ,\"InLibraryUse\", \"SupervisedUse\" ] ,\"pageSize\": 10}";
        MvcResult mvcResult = this.mockMvc.perform(post("/searchService/search").param("requestJson",paramString)
                                                                                .contentType(contentType)
                                                                                .content(objectMapper.writeValueAsString(searchRecordsRequest))).andReturn();
        int status = mvcResult.getResponse().getStatus();

        assertTrue(status == 200);
    }

    @Test
    public void searchRestfulApiSupervisedUse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest();
        searchRecordsRequest.setOwningInstitutions(Arrays.asList("PUL"));
        searchRecordsRequest.setMaterialTypes(Arrays.asList("Other"));
        searchRecordsRequest.setUseRestrictions(Arrays.asList("SupervisedUse"));
        searchRecordsRequest.setPageSize(10);
        String paramString="{\"owningInstitutions\" : [\"PUL\" ],\"materialTypes\" : [ \"Other\" ],\"useRestrictions\" : [\"SupervisedUse\" ] ,\"pageSize\": 10}";
        MvcResult mvcResult = this.mockMvc.perform(post("/searchService/search")
                .param("requestJson",paramString)
                .contentType(contentType)
                .content(objectMapper.writeValueAsString(searchRecordsRequest)))
                .andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertTrue(status == 200);
    }

    @Test
    public void searchRestfulApiUseRestriction() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest();
        searchRecordsRequest.setOwningInstitutions(Arrays.asList("PUL"));
        searchRecordsRequest.setMaterialTypes(Arrays.asList("Other"));
        searchRecordsRequest.setUseRestrictions(Arrays.asList("NoRestrictions","SupervisedUse"));
        searchRecordsRequest.setPageSize(10);
        String paramString="{\"owningInstitutions\" : [\"PUL\" ],\"materialTypes\" : [ \"Other\" ],\"useRestrictions\" : [\"NoRestrictions\" , \"SupervisedUse\" ] ,\"pageSize\": 10}";
        MvcResult mvcResult = this.mockMvc.perform(post("/searchService/search")
                .param("requestJson",paramString)
                .contentType(contentType)
                .content(objectMapper.writeValueAsString(searchRecordsRequest)))
                .andReturn();
        logger.info(mvcResult.getResponse().getContentAsString());
        int status = mvcResult.getResponse().getStatus();
        assertTrue(status == 200);
    }

    @Test
    public void searchRestfulRequestParamOwningInstitute() throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(get("/searchService/searchByParam")
                        .param("pageSize","10")
                        .param("owningInstitutions","PUL")
                )
                .andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertTrue(status == 200);
    }

    @Test
    public void searchRestfulRequestParamOnlyRequired() throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(get("/searchService/searchByParam")
                .param("pageSize","10")
                .param("owningInstitutions","PUL")
        )
                .andReturn();
        logger.info(mvcResult.getResponse().getContentAsString());
        String strJson = mvcResult.getResponse().getContentAsString();
        ObjectMapper om =new ObjectMapper();

        SearchResultRow[] searchResultRowAr =om.readValue(strJson, SearchResultRow[].class);
//        List<SearchResultRow> searchResultRows= Arrays.asList(searchResultRowAr);
        List<SearchResultRow> searchResultRowL=new ArrayList<>(Arrays.asList(searchResultRowAr));

//        searchResultRows = om.readValue(strJson, ArrayList.class);
        // BIBID = 24136
        assertTrue(mvcResult.getResponse().getStatus() == 200);
        SearchResultRow searchResultRow=null;
        if(searchResultRowL.size()>0) {
            searchResultRow = (SearchResultRow) searchResultRowL.get(0);
        }
        int iBibiid=searchResultRow.getBibId().intValue();
        assertNotNull(iBibiid);

        /*
        *   myPojo[] pojos = objectMapper.readValue(json, MyPojo[].class);
        *   List<MyPojo> pojoList = Arrays.asList(pojos);
        *   List<MyPojo> mcList = new ArrayList<>(Arrays.asList(pojos));
        *
        * */
    }


    String solrClientUrl="http://localhost:9090/";
//    http://localhost:9090/searchService/search?requestJson
    @Test
    public void getBibsFromSolr() throws Exception {
        SearchRecordsRequest searchRecordsRequest = new SearchRecordsRequest();
        searchRecordsRequest.setOwningInstitutions(Arrays.asList("PUL"));
        searchRecordsRequest.setCollectionGroupDesignations(Arrays.asList("Shared"));
        searchRecordsRequest.setPageSize(10);

        RestTemplate restTemplate = new RestTemplate();
        String url = solrClientUrl + "searchService/searchRecords?requestJson="+"{\n" +
                "  \"fieldValue\": \"\",\n" +
                "  \"fieldName\": \"\",\n" +
                "  \"owningInstitutions\" : [ \"PUL\" ],\n" +
                "  \"useRestrictions\" : [ \"NoRestrictions\" ],\n" +
                "  \"pageSize\": 10\n" +
                "}";


        HttpHeaders headers = new HttpHeaders();
        headers.set("api_key","recap");
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);

        HttpEntity<SearchRecordsRequest> requestEntity = new HttpEntity<>(searchRecordsRequest,headers);
//        ResponseEntity<String> result = restTemplate.exchange(url, HttpMethod.GET,entity , String.class);
//        List ls  = restTemplate.getForEntity(url,List.class);

//        assertTrue(responseEntity.getStatusCode().getReasonPhrase().equalsIgnoreCase("OK"));
//        Map responseEntityBody = responseEntity.getBody();
//        Integer totalPageCount = (Integer) responseEntityBody.get("totalPageCount");
//        String totalBibsCount = (String) responseEntityBody.get("totalBibsCount");
//        String totalItemsCount = (String) responseEntityBody.get("totalItemsCount");

//        List searchResultRows = (List) responseEntityBody.get("searchResultRows");
//        assertNotNull(totalPageCount);
//        assertNotNull(totalBibsCount);
//        assertNotNull(totalItemsCount);
//        assertNotNull(searchResultRows);
//        System.out.println("Total Pages : " + totalPageCount);
//        System.out.println("Total Bibs : " + totalBibsCount);
//        System.out.println("Total Items : " + totalItemsCount);
//        System.out.println("Search Result Rows : " + searchResultRows);
        logger.info("end");
    }
}
