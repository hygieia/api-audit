package com.capitalone.dashboard.request;

import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

public class AuditReviewRequest {
	@ApiModelProperty(value = "Begin Date", example="1521222841800")
    @NotNull
    private long beginDate;
    @ApiModelProperty(value = "End Date", example="1521222842000")
	@NotNull
    private long endDate;
    @ApiModelProperty(value = "Unique Client Reference Id", example="someuniquestring")
    private String clientReference;

    public long getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(long beginDate) {
        this.beginDate = beginDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

}
