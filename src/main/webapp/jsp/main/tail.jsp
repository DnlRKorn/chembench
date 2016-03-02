<script>
  (function() {
    "use strict";

    // Chembench-specific JS globals
    window.Chembench = {
      "MODI_MODELABLE": "<s:property value="@edu.unc.ceccr.chembench.global.Constants@MODI_MODELABLE" />",
      "DATATABLE_OPTIONS": {
        "columnDefs": [{
          orderable: false,
          targets: "unsortable"
        }],
        "paging": false,
        "dom": "lifrtp",
        "infoCallback": function(settings, start, end, max, total) {
          var totalNoun = (total === 1) ? "entry" : "entries";
          var maxNoun = (max === 1) ? "entry" : "entries";
          if (max !== total) {
            return "Showing " + total + " " + totalNoun + " (filtered from " + max + " total " + maxNoun + ")";
          }
          return "Showing " + max + " " + maxNoun;
        }
      },
      "Constants": {
        "MODELING": "<s:property value="@edu.unc.ceccr.chembench.global.Constants@MODELING" />",
        "MODELINGWITHDESCRIPTORS": "<s:property
        value="@edu.unc.ceccr.chembench.global.Constants@MODELINGWITHDESCRIPTORS" />",
        "CONTINUOUS": "<s:property value="@edu.unc.ceccr.chembench.global.Constants@CONTINUOUS" />",
        "CATEGORY": "<s:property value="@edu.unc.ceccr.chembench.global.Constants@CATEGORY" />",
        "UPLOADED": "<s:property value="@edu.unc.ceccr.chembench.global.Constants@UPLOADED" />"
      }
    };
  })();
</script>

<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
<script src="https://cdn.datatables.net/t/bs-3.3.6/dt-1.10.11/datatables.min.js"></script>
<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/js/bootstrap.min.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/bootbox.min.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/common.js"></script>
<script src="${pageContext.request.contextPath}/assets/js/common.datatables.js"></script>
