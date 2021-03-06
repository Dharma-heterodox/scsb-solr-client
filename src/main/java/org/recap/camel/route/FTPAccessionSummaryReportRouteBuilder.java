package org.recap.camel.route;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.BindyType;
import org.recap.RecapConstants;
import org.recap.model.csv.AccessionSummaryRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by hemalathas on 23/11/16.
 */
@Component
public class FTPAccessionSummaryReportRouteBuilder {
    @Autowired
    public FTPAccessionSummaryReportRouteBuilder(CamelContext context,
                                                   @Value("${ftp.userName}") String ftpUserName, @Value("${ftp.remote.server}") String ftpRemoteServer,
                                                   @Value("${ftp.knownHost}") String ftpKnownHost, @Value("${ftp.privateKey}") String ftpPrivateKey) {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from(RecapConstants.FTP_ACCESSION_SUMMARY_REPORT_Q)
                            .routeId(RecapConstants.FTP_ACCESSION_SUMMARY_REPORT_ID)
                            .marshal().bindy(BindyType.Csv, AccessionSummaryRecord.class)
                            .to("sftp://" + ftpUserName + "@" + ftpRemoteServer + "?privateKeyFile=" + ftpPrivateKey + "&knownHostsFile=" + ftpKnownHost + "&fileName=${in.header.fileName}-${date:now:ddMMMyyyyHHmmss}.csv&fileExist=append");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
