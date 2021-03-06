<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>

<table class="table table-hover table-bordered datatable" data-url="<s:url action="getPredictions" namespace="/api"
/>" data-object-type="prediction"
    <s:if test="#showAll != true">
      data-paging="data-paging"
    </s:if>
>
  <thead>
  <tr>
    <th data-property="name">Name</th>
    <th data-property="datasetDisplay">Dataset Predicted</th>
    <th data-property="predictorNames">Predictor(s) Used</th>
    <th data-property="similarityCutoff">Cutoff</th>
    <th data-property="dateCreated" data-sort-direction="desc" class="date-created">Date</th>
  </tr>
  </thead>
  <tbody></tbody>
</table>
