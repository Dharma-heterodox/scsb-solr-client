package org.recap.service.partnerservice;

import org.recap.RecapConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;

/**
 * Created by premkb on 18/12/16.
 */
@Service
public class PrincetonService {

    private static final Logger logger = LoggerFactory.getLogger(PrincetonService.class);

    @Value("${ils.princeton.bibdata}")
    String ilsprincetonBibData;

    public String getBibData(String itemBarcode) {
        RestTemplate restTemplate = new RestTemplate();
        HostnameVerifier verifier = new NullHostnameVerifier();
        SCSBSimpleClientHttpRequestFactory factory = new SCSBSimpleClientHttpRequestFactory(verifier);
        restTemplate.setRequestFactory(factory);

        String bibDataResponse = null;
        String response = null;
        try {
            bibDataResponse = restTemplate.getForObject(ilsprincetonBibData + itemBarcode, String.class);
        } catch (HttpClientErrorException e) {
            logger.error(RecapConstants.ITEM_BARCODE_NOT_FOUND);
            response = RecapConstants.ITEM_BARCODE_NOT_FOUND;
            throw new RuntimeException(response);
        } catch (Exception e) {
            logger.error(RecapConstants.SERVICE_UNAVAILABLE);
            response = ilsprincetonBibData + RecapConstants.SERVICE_UNAVAILABLE;
            throw new RuntimeException(response);
        }
        return bibDataResponse;
    }
}