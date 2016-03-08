package edu.unc.ceccr.chembench.actions;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import edu.unc.ceccr.chembench.global.Constants;
import edu.unc.ceccr.chembench.persistence.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ModelDetailAction extends DetailAction {
    private static final Logger logger = Logger.getLogger(ModelDetailAction.class);
    private final DatasetRepository datasetRepository;
    private final PredictorRepository predictorRepository;
    private final RandomForestGroveRepository randomForestGroveRepository;
    private final RandomForestTreeRepository randomForestTreeRepository;
    private final SvmModelRepository svmModelRepository;
    private final KnnModelRepository knnModelRepository;
    private final KnnPlusModelRepository knnPlusModelRepository;
    private final RandomForestParametersRepository randomForestParametersRepository;
    private final KnnParametersRepository knnParametersRepository;
    private final KnnPlusParametersRepository knnPlusParametersRepository;
    private final SvmParametersRepository svmParametersRepository;
    private List<?> models;
    private List<?> yRandomModels;
    private Predictor predictor;
    private Dataset modelingDataset;
    private Object modelParameters;
    private boolean editable;
    private Iterable<Integer> foldNumbers;
    private boolean isYRandom;
    private int foldNumber;
    private List<?> data;

    @Autowired
    public ModelDetailAction(DatasetRepository datasetRepository, PredictorRepository predictorRepository,
                             RandomForestGroveRepository randomForestGroveRepository,
                             RandomForestTreeRepository randomForestTreeRepository,
                             SvmModelRepository svmModelRepository, KnnModelRepository knnModelRepository,
                             KnnPlusModelRepository knnPlusModelRepository,
                             RandomForestParametersRepository randomForestParametersRepository,
                             KnnParametersRepository knnParametersRepository,
                             KnnPlusParametersRepository knnPlusParametersRepository,
                             SvmParametersRepository svmParametersRepository) {
        this.datasetRepository = datasetRepository;
        this.predictorRepository = predictorRepository;
        this.randomForestGroveRepository = randomForestGroveRepository;
        this.randomForestTreeRepository = randomForestTreeRepository;
        this.svmModelRepository = svmModelRepository;
        this.knnModelRepository = knnModelRepository;
        this.knnPlusModelRepository = knnPlusModelRepository;
        this.randomForestParametersRepository = randomForestParametersRepository;
        this.knnParametersRepository = knnParametersRepository;
        this.knnPlusParametersRepository = knnPlusParametersRepository;
        this.svmParametersRepository = svmParametersRepository;
    }

    public String execute() {
        predictor = predictorRepository.findOne(id);
        String result = validateObject(predictor);
        if (!result.equals(SUCCESS)) {
            return result;
        }
        editable = predictor.isEditableBy(user);
        modelingDataset = datasetRepository.findOne(predictor.getDatasetId());
        predictor.setDatasetDisplay(modelingDataset.getName());
        if (predictor.getChildType().equals(Constants.NFOLD)) {
            List<Predictor> childPredictors = predictorRepository.findByParentId(predictor.getId());
            foldNumbers = ContiguousSet.create(Range.closed(1, childPredictors.size()), DiscreteDomain.integers());
        } else {
            models = readModels(predictor, false);
            yRandomModels = readModels(predictor, true);
        }
        switch (predictor.getModelMethod()) {
            case Constants.RANDOMFOREST:
            case Constants.RANDOMFOREST_R:
                modelParameters = randomForestParametersRepository.findOne(predictor.getModelingParametersId());
                break;
            case Constants.KNN: // legacy knn
                modelParameters = knnParametersRepository.findOne(predictor.getModelingParametersId());
                break;
            case Constants.KNNGA: // knn+
            case Constants.KNNSA:
                modelParameters = knnPlusParametersRepository.findOne(predictor.getModelingParametersId());
                break;
            case Constants.SVM:
                modelParameters = svmParametersRepository.findOne(predictor.getModelingParametersId());
                break;
            default:
                logger.warn(
                        String.format("Unrecognized model method %s for predictor id %d", predictor.getModelMethod(),
                                predictor.getId()));
                break;
        }
        return SUCCESS;
    }

    public String getFold() {
        predictor = predictorRepository.findOne(id);
        String result = validateObject(predictor);
        if (!result.equals(SUCCESS)) {
            return result;
        }
        List<Predictor> childPredictors = predictorRepository.findByParentId(predictor.getId());
        if (foldNumber < 1 || foldNumber > childPredictors.size()) {
            return "badrequest";
        }
        data = readModels(childPredictors.get(foldNumber - 1), isYRandom);
        return SUCCESS;
    }

    private List<?> readModels(Predictor predictor, boolean isYRandom) {
        String isYRandomString = (isYRandom) ? Constants.YES : Constants.NO;
        switch (predictor.getModelMethod()) {
            case Constants.RANDOMFOREST:
            case Constants.RANDOMFOREST_R:
                RandomForestGrove grove = randomForestGroveRepository
                        .findByPredictorIdAndIsYRandomModel(predictor.getId(), isYRandomString);
                return randomForestTreeRepository.findByRandomForestGroveId(grove.getId());
            case Constants.SVM:
                return svmModelRepository.findByPredictorIdAndIsYRandomModel(predictor.getId(), isYRandomString);
            case Constants.KNNSA: // knn+
            case Constants.KNNGA:
                return knnPlusModelRepository.findByPredictorIdAndIsYRandomModel(predictor.getId(), isYRandomString);
            case Constants.KNN: // legacy knn
                return (isYRandom) ? Lists.newArrayList() : knnModelRepository.findByPredictorId(predictor.getId());
        }
        logger.warn("Unrecognized model method: " + predictor.getModelMethod());
        return Lists.newArrayList();
    }

    public List<?> getModels() {
        return models;
    }

    public void setModels(List<?> models) {
        this.models = models;
    }

    public List<?> getYRandomModels() {
        return yRandomModels;
    }

    public void setYRandomModels(List<?> yRandomModels) {
        this.yRandomModels = yRandomModels;
    }

    public Predictor getPredictor() {
        return predictor;
    }

    public void setPredictor(Predictor predictor) {
        this.predictor = predictor;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public Iterable<Integer> getFoldNumbers() {
        return foldNumbers;
    }

    public void setFoldNumbers(Iterable<Integer> foldNumbers) {
        this.foldNumbers = foldNumbers;
    }

    public boolean getIsYRandom() {
        return isYRandom;
    }

    public void setIsYRandom(boolean YRandom) {
        isYRandom = YRandom;
    }

    public int getFoldNumber() {
        return foldNumber;
    }

    public void setFoldNumber(int foldNumber) {
        this.foldNumber = foldNumber;
    }

    public List<?> getData() {
        return data;
    }

    public void setData(List<?> data) {
        this.data = data;
    }

    public Dataset getModelingDataset() {
        return modelingDataset;
    }

    public void setModelingDataset(Dataset modelingDataset) {
        this.modelingDataset = modelingDataset;
    }

    public Object getModelParameters() {
        return modelParameters;
    }

    public void setModelParameters(Object modelParameters) {
        this.modelParameters = modelParameters;
    }
}
