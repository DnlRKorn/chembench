<%@ taglib prefix="s" uri="/struts-tags"%>
<%@ page import="edu.unc.ceccr.chembench.global.Constants"%>

<!DOCTYPE html>
<html>
<head>
<%@ include file="/jsp/main/head.jsp"%>
<title>Chembench | View Dataset</title>
</head>
<body>
  <div id="main" class="container">
    <%@ include file="/jsp/main/header.jsp"%>

    <section id="content">
      <h2>
        Dataset Details:
        <s:property value="dataset.name" />
        <s:a action="jobs" anchor="datasets">
          <button class="btn btn-primary">Back to Datasets</button>
        </s:a>
      </h2>

      <div class="list-group">
        <div class="list-group-item">
          <h4 class="list-group-item-heading">General information</h4>
          <dl class="dl-horizontal properties-list">
            <dt>Dataset type</dt>
            <dd>
              <s:property value="datasetTypeDisplay" />
            </dd>
            <dt>Number of compounds</dt>

            <dd>
              <s:property value="dataset.numCompound" />
            </dd>

            <dt>Activity type</dt>
            <dd>
              <s:if test="dataset.modelType.equals(@edu.unc.ceccr.chembench.global.Constants@PREDICTION)">
            None
          </s:if>
              <s:else>
                <div class="activity-type">
                  <s:property value="dataset.modelType" />
                </div>
              </s:else>
            </dd>

            <dt>Modelability index</dt>
            <dd>
              <s:if test="!dataset.canGenerateModi()">
                <span class="text-muted">MODI cannot be generated for this dataset.</span>
              </s:if>
              <s:else>
                <s:if test="dataset.modiGenerated">
                  <s:if test="dataset.modi >= @edu.unc.ceccr.chembench.global.Constants@MODI_MODELABLE">
                    <span class="modi-value text-success" title="Modelable"> <s:property
                        value="getText('{0, number, #, ##0.00}', {dataset.modi})" /></span>
                  </s:if>
                  <s:else>
                    <span class="modi-value text-warning" title="Not modelable"> <s:property
                        value="getText('{0, number, #, ##0.00}', {dataset.modi})" /></span>
                  </s:else>
                </s:if>
                <s:else>
                  <input type="hidden" name="dataset-id" value="<s:property value="dataset.id" />">
                  <span class="text-warning">Not generated</span>
                  <button class="btn btn-primary btn-xs generate-modi">Generate MODI</button>
                </s:else>
              </s:else>
            </dd>

            <dt>Date created</dt>
            <dd>
              <s:date name="dataset.createdTime" format="yyyy-MM-dd HH:mm" />
            </dd>
          </dl>
        </div>

        <div class="list-group-item">
          <h4 class="list-group-item-heading">Descriptors</h4>
          <dl class="dl-horizontal properties-list">
            <dt>Descriptors available</dt>
            <dd class="available-descriptors">
              <s:property value="dataset.availableDescriptors" />
            </dd>

            <s:if test="!dataset.uploadedDescriptorType.isEmpty()">
              <dt>Uploaded descriptor type</dt>
              <dd>
                <s:property value="dataset.uploadedDescriptorType" />
              </dd>
            </s:if>
          </dl>
        </div>

        <div class="list-group-item">
          <h4 class="list-group-item-heading">
            Description and paper reference
            <button id="edit-description-reference" class="btn btn-primary btn-xs">
              <span class="glyphicon glyphicon-pencil"></span> Edit
            </button>
            <span id="description-reference-buttons">
              <button id="cancel-changes" class="btn btn-default btn-xs">
                <span class="glyphicon glyphicon-remove"></span> Cancel
              </button>
              <button id="save-changes" type="submit" class="btn btn-primary btn-xs">
                <span class="glyphicon glyphicon-floppy-disk"></span> Save
              </button>
            </span>
          </h4>
          <dl id="description-reference-text" class="properties-list">
            <dt>Description</dt>
            <dd id="description">
              <s:if test="dataset.description.isEmpty()">
                (No description given.)
              </s:if>
              <s:else>
                <s:property value="dataset.description" />
              </s:else>
            </dd>

            <dt>Paper reference</dt>
            <dd id="paper-reference">
              <s:if test="dataset.paperReference.isEmpty()">
                (No paper reference given.)
              </s:if>
              <s:else>
                <s:property value="dataset.paperReference" />
              </s:else>
            </dd>
          </dl>

          <s:form action="updateDataset" enctype="multipart/form-data" theme="simple">
            <div class="form-group">
              <label for="datasetDescription">Description:</label>
              <s:textarea id="datasetDescription" name="datasetDescription" value="%{dataset.description}"
                cssClass="form-control" />
            </div>

            <div class="form-group">
              <label for="datasetReference">Paper reference:</label>
              <s:textarea id="datasetReference" name="datasetReference" value="%{dataset.paperReference}"
                cssClass="form-control" />
            </div>

            <s:hidden id="objectId" name="objectId" />
            <s:hidden id="description" name="description" value="%{dataset.description}" />
            <s:hidden id="paperReference" name="paperReference" value="%{dataset.paperReference}" />
          </s:form>
        </div>
      </div>

      <hr>

      <ul class="nav nav-tabs">
        <li class="active"><a href="#compound-list" data-toggle="tab">Compound List</a></li>
        <li><a href="#external-set" data-toggle="tab">External Set</a></li>
        <li><a href="#descriptors" data-toggle="tab">Descriptors</a></li>
        <li><a href="#activity-histogram" data-toggle="tab">Activity Histogram</a></li>
        <li><a href="#heatmap" data-toggle="tab">Heatmap</a></li>
      </ul>

      <div class="tab-content">
        <div id="compound-list" class="tab-pane active">
          <h3>Compound List</h3>
          <p class="tab-description">Bacon ipsum dolor amet excepteur ground round fugiat labore commodo ut kevin in
            ullamco meatloaf. In et non sirloin est occaecat consectetur biltong eu in sint. Ut consectetur boudin
            tempor elit non, sausage do. Jowl picanha voluptate, duis tail salami ut fugiat sirloin pastrami laboris
            nostrud boudin veniam. Excepteur mollit venison ball tip sint. Tri-tip commodo est in. Irure ground round
            esse, turducken in shank nostrud voluptate exercitation cillum short loin.</p>
        </div>

        <div id="external-set" class="tab-pane">
          <h3>External Set</h3>
          <p class="tab-description">Fatback deserunt dolor, chicken pariatur est swine. Hamburger aliqua non
            picanha ut eu spare ribs. Kielbasa voluptate bresaola magna, id pork exercitation. Kevin nostrud corned beef
            sint. Hamburger ut landjaeger, nisi sausage pork loin drumstick pariatur prosciutto.</p>
        </div>

        <div id="descriptors" class="tab-pane">
          <h3>Descriptors</h3>

          <p class="tab-description">Boudin ad laboris, jowl cillum in excepteur doner. Tempor et velit tail, in
            corned beef aliquip est tongue ut qui cupidatat frankfurter. Lorem elit quis capicola ut nulla flank tempor
            voluptate consectetur corned beef brisket labore mollit andouille. Pork belly tempor exercitation tongue
            cupidatat consectetur andouille lorem et aute short ribs ham hock. Ea veniam adipisicing occaecat, strip
            steak tail sunt cow alcatra laboris aute proident eu.</p>
        </div>

        <div id="activity-histogram" class="tab-pane">
          <h3>Activity Histogram</h3>
          <p class="tab-description">Mollit pig brisket, shankle id commodo qui. Ut est aliqua, commodo in ham hock
            exercitation short ribs flank. Ex non cupim, brisket id sed aliquip ipsum kevin pork belly dolore proident
            tri-tip prosciutto flank. Incididunt pig quis ut dolore veniam tenderloin tri-tip dolore in cupidatat esse
            tail andouille. Irure rump laboris, do turkey shank ullamco shoulder pork chop.</p>
        </div>

        <div id="heatmap" class="tab-pane">
          <h3>Heatmap</h3>
          <p class="tab-description">Voluptate non jowl ribeye irure sirloin ullamco adipisicing alcatra ham hock
            beef. Boudin corned beef labore, salami minim qui occaecat. Cow enim magna meatloaf reprehenderit capicola.
            Culpa frankfurter chicken dolore, ribeye pork loin ea cupidatat fatback labore andouille rump tongue eiusmod
            ad.</p>
        </div>

      </div>
    </section>

    <%@include file="/jsp/main/footer.jsp"%>
  </div>

  <%@ include file="/jsp/main/tail.jsp"%>
  <script src="assets/js/viewDataset.js"></script>
</body>
</html>

