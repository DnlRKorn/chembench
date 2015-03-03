<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<!DOCTYPE html>
<html>
<head>
  <%@ include file="/jsp/main/head.jsp" %>
  <title>Chembench | My Bench</title>
</head>
<body>
<div id="main" class="container">
  <%@ include file="/jsp/main/header.jsp" %>

  <section id="content">
    <h2>My Bench</h2>

    <p>Every dataset, predictor, and prediction you have created on Chembench is available on this page. You can
      track progress of all the running jobs using the job queue.</p>

    <p>
      Publicly available datasets and models are also displayed. If you wish to share datasets or models you
      have developed with the Chembench community, please contact us at <a href="mailto:ceccr@email.unc.edu">ceccr@email.unc.edu</a>.
    </p>

    <p>All data is sorted by the creation date in descending order (newest on top).</p>

    <ul class="nav nav-tabs">
      <li class="active"><a href="#jobs" data-toggle="tab">Job Queue</a></li>
      <li><a href="#datasets" data-toggle="tab">Datasets</a></li>
      <li><a href="#models" data-toggle="tab">Models</a></li>
      <li><a href="#predictions" data-toggle="tab">Predictions</a></li>
    </ul>

    <div class="tab-content">
      <%@ include file="jobs.jsp" %>

      <div id="datasets" class="tab-pane">
        <h3>Datasets</h3>
        <%@ include file="datasets.jsp" %>
      </div>

      <div id="models" class="tab-pane">
        <h3>Models</h3>
        <%@ include file="models.jsp" %>
      </div>

      <div id="predictions" class="tab-pane">
        <h3>Predictions</h3>
        <%@ include file="predictions.jsp" %>
      </div>
    </div>
  </section>

  <%@ include file="/jsp/main/footer.jsp" %>
</div>

<%@ include file="/jsp/main/tail.jsp" %>
<script src="/assets/js/mybench.js"></script>
</body>
</html>
