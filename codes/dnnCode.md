# A Fully Connected Deep Neural Network for Crop classification

### Loading Package

````r
suppressPackageStartupMessages({
  library(tensorflow)
  library(keras)
  library(tidyverse)
  library(sf)
  
})
````



### Defining working directory and reading dataset



````R

setwd("D:/RWork/CSE 2022/report arc")
dn<-dir('.','csv');dn

db<- dn %>% 
  read.csv()%>% 
  select(!c(1,16)) 

fn<-dir('.','shp')

training<-st_read(fn)
df<-merge(training,db, by='ID')

data<- df %>% 
  st_drop_geometry() %>%
  select(!1) %>% mutate(landcover=as.factor(landcover))


str(data)

indx<-c(2:7)
# indx<-c(4,7,11,12,14)
names(data)[indx]

data[,indx]<-lapply(data[,indx], scale)



````



### Data formatting



````R
nn_dat = data %>%
  as_tibble %>%
  mutate(class_num    = as.numeric(landcover) - 1,
         class_label  = landcover) 
nn_dat %>% head(3)


test_f = 0.20
nn_dat = nn_dat %>%
  mutate(partition = sample(c('train','test'), nrow(.), replace = TRUE, prob = c(1 - test_f, test_f)))


x_train = nn_dat %>% dplyr::filter(partition == 'train') %>% dplyr::select(all_of(indx)) %>% as.matrix
y_train = nn_dat %>% dplyr::filter(partition == 'train') %>% pull(class_num) %>% to_categorical(8)
x_test  = nn_dat %>% dplyr::filter(partition == 'test')  %>% dplyr::select(all_of(indx)) %>% as.matrix
y_test  = nn_dat %>% dplyr::filter(partition == 'test')  %>% pull(class_num) %>% to_categorical(8)

````



### Model architecture 



````R

FLAGS <- flags(
  flag_numeric("dropout1", 0.4),
  flag_numeric("dropout2", 0.3)
)

input.shape<-length(indx)
input.shape



build_model<-function(){
  
  model<-keras_model_sequential() %>%  
    layer_dense(units = 128,activation = 'relu', input_shape = input.shape) %>% 
    layer_batch_normalization() %>% 
    layer_dropout(FLAGS$dropout1) %>% 
    layer_dense(units = 128,activation = 'relu') %>%  
    layer_batch_normalization() %>% 
    layer_dense(units = 64,activation = 'relu') %>% 
    layer_batch_normalization() %>% 
    layer_dropout(FLAGS$dropout2) %>% 
    layer_dense(units = 10,activation = 'relu') %>%
    layer_dense(units = 8, activation = 'softmax')
  
  model %>% compile(
    optimizer=optimizer_rmsprop(), 
    loss='categorical_crossentropy', 
    metrics=list('accuracy', 
                 metric_true_negatives(),
                 metric_true_positives(),
                 metric_false_positives(),
                 metric_false_negatives())
  )
  
  
}

model<-build_model()

history<-model %>% 
  fit(
    x = x_train,
    y = y_train,
    epochs           = 200,
    batch_size       = 1000,
    validation_split = 0, 
    callbacks=callback_tensorboard()
  )

# model %>% save_model_hdf5("Models/model6bands.h5")
# # plot(history)
# histdf2<-as.data.frame(history)
# write.csv(histdf2,"Models/hsitory6Bands.csv")

plot(history)
# c(loss, mae) %<-% (model %>% evaluate(x_test, y_test, verbose = 0))
# c(loss, mae)
y_pred <- model %>% 
  predict(x_test) %>% round(0)

y_pred <-round(y_pred ,0)

conf1<-caret::confusionMatrix(table(y_pred,y_test))
conf2<-caret::confusionMatrix(table(y_pred,y_test), mode = "prec_recall")

cat('this is the confusion matrix 2 with metrics described above', ' in the introduction')
conf1
conf2


````

### Visualization of the Results

````R

# then we can augments the nn_dat for plotting

plot_dat = nn_dat %>% filter(partition == 'test') %>%
  mutate(class_num = factor(class_num),
         y_pred    = factor(predict_classes(model, x_test)),
         Correct   = factor(ifelse(class_num == y_pred, "Yes", "No")))
class(plot_dat)
names(plot_dat)
plot_dat %>% select(all_of(indx)) %>% head(3)

perf = model %>% evaluate(x_test, y_test)
print(perf)


# and lastly, we can visualise the confusion matrix like si: 
title     = "Classification Performance of Artificial Neural Network"
sub_title = str_c("Accuracy = ", round(perf$acc, 3) * 100, "%")
x_lab     = "True LULC class"
y_lab     = "Predicted LULC class"
plot_dat %>% ggplot(aes(x = class_num, y = y_pred, colour = Correct)) +
  geom_jitter() +
  scale_x_discrete(labels = levels(nn_dat$class_label)) +
  scale_y_discrete(labels = levels(nn_dat$class_label)) +
  theme(legend.position ='bottom')+
  labs(title = title, subtitle = sub_title, x = x_lab, y = y_lab)
````

