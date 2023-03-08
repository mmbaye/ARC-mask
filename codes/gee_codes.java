

var regions=table.filter(ee.Filter.eq("ADM1_NAME","Trarza"))


var startDate = '2022-07-01';
var endDate = '2022-07-30';

var CLOUD_COVERAGE_ASSESSMENT = 5  


Map.setOptions('satellite')
// Map.addLayer(imag)
// Map.addLayer(imag,{},'aoi',false)

var vegetationIndex=function(image){
            var ndvi  = image.normalizedDifference(['B8','B4']).rename('NDVI')
            var ndwia = image.normalizedDifference(['B8','B11']).rename('NDWIA')
            var ndwib = image.normalizedDifference(['B3','B8']).rename('NDWIB')
            var ndbi  = image.normalizedDifference(["B11","B8"]).rename("NDBI")
            var ic    = image.normalizedDifference(['B4','B3']).rename('IC')
            var nbai  = image.expression('((swir2-nir)/blue) / ((swir2+nir)/blue)',
                          { swir2: image.select('B12'),
                          nir : image.select('B8'),
                          blue : image.select('B2') }).rename('NBAI');
            var ri    = image.expression('(red*red) / (green*green)',
                          {red: image.select('B4'),
                          green: image.select('B3')}).rename('RI') ;
            var ibb   = image.expression('sqrt(((red*red) + (green*green) +(nir*nir))/3)',
                          {red: image.select('B4'),
                          green: image.select('B3'),
                          nir:image.select('B8') } ).rename('IBB') ;
            var iba   = image.expression('sqrt(((red*red) + (green*green))/2)', 
                         {red: image.select('B4'),
                         green: image.select('B3')}).rename('IBA');
            return image.addBands([ndvi,ndwia,ndwib,ndbi,ic,ibb,iba]).copyProperties(image, ['system:time_start']);
                         
          }   


/********************************* Sentinel-2 image processing *******************************************
 *  We will 
 * **********************************************************************************************************/  
 
var S2=ee.ImageCollection('COPERNICUS/S2')
        .filterDate(startDate,endDate)
        .filterBounds(regions)
        .filter(ee.Filter.eq('MGRS_TILE','28QDD'))
        .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE',1))
        .select('B.*')
        .map(function(image){return image.clip(regions).divide(10000).copyProperties(image,['system:time_start'])})
        .map(vegetationIndex)

Map.centerObject(S2,10)
print(S2)
Map.addLayer(S2,{bands:['B11','B8', 'B4'],min:0.17, max:0.7}, 'False color', false)



/****************************************** visualization map *********************
 *  visualization
 * ********************************************* fin ***************************************/ 

// var habitat=training.filter(ee.Filter.eq('landcover','build'))
// var crop=training.filter(ee.Filter.eq('landcover','crops'))
// var soil=training.filter(ee.Filter.eq('landcover','soils'))
// var water=training.filter(ee.Filter.eq('landcover','water'))

// Map.addLayer(habitat.draw('red',3,3),{},'habitat')
// Map.addLayer(crop.draw('green',3,3),{},'crop')
// Map.addLayer(soil.draw('gray',3,3),{},'soil')
// Map.addLayer(water.draw('blue',3,3),{},'water')

// print(training.aggregate_histogram('landcover'))
// var allclasse=ee.FeatureCollection(training).remap(["build","crops","soils","water"],[0,1,2,3],"labels");
// Map.addLayer(allclasse,{},'all classes');




var dataset=S2.mean()

 
 
/****************************************** visualization fin **********************/ 

var training_lulc = dataset.sampleRegions({
  collection: training, 
  properties: ['landcover'],  
  scale: 10,
  tileScale:2
});


print(training_lulc.limit(10))

var chart=function(f,xband,yband){
  
  var graphx=ui.Chart.feature.groups({
    features:f, 
    xProperty: xband,
    yProperty: yband,
    seriesProperty:'landcover'
    
  }).setChartType('ScatterChart')
  
  return graphx
  
  
}

print('Check the size of the training points: ',training_lulc.size()) // 1214
// // print('info training : ',training_lulc)


// // var histogram = training_lulc.reduceColumns({
// //   reducer: ee.Reducer.frequencyHistogram(), 
// //   selectors: ['ID']
// // });

// // print(histogram);


// // // // // // ::::::::::  Exploring the dataset you can export them ::::::::::::::::::::::::::::::::::::
print('graphe 1', chart(training_lulc, 'B8','B4'))
print('graphe 2', chart(training_lulc, 'B11','B3'))
print('graphe 3', chart(training_lulc, 'B12','B2'))
print('graphe 4', chart(training_lulc, 'B6','B7'))
print('graphe 5', chart(training_lulc, 'B5','B12'))




// //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

// set new collection of bands

var bands=['B2','B3','B4', 'B8','B11','B12','NDVI','NDWIA']

var training_sample = dataset.select(bands).sampleRegions({
  collection: training, 
  properties: ['ID'],
  scale: 10
});

print('trainig sample  size : ',training_sample.size())

// Export.table.toDrive(training_sample)

var classifier = ee.Classifier.libsvm({
  kernelType: 'RBF',
  gamma: 0.32,
  cost: 8
});

var withRandom = training_sample.randomColumn();

var trainingPartition = withRandom.filter(ee.Filter.lt('random', 0.5));
var testingPartition = withRandom.filter(ee.Filter.gte('random', 0.5));

var trained = classifier.train(trainingPartition, 'ID',bands)

var classified = dataset.select(bands).classify(trained)



var mapViz = {min:0, max:7,palette:["red","green","gray","blue"] }

var mapViz  = {
  min: 0,
  max: 7,
  palette: [
    '#cb181d',// building (0)
    '3bad5e',// crops (1)
    'yellow',// flooded vegetation, (2)
    '#ffffff',// Grass, (3)
    '#ffbf82',// scrub, (4)
    '#fff3b1',// soil, (5)
    '#2d3639',// tree, (6)
    '#397df9',// water, (7)
  ]
};

// area calculation


// CHART OPTIONS:
var options = {
  lineWidth: 1,
  pointSize: 2,
  hAxis: {title: 'Classes'},
  vAxis: {title: 'Area m²'},
  title: 'Area by class',
  series: {
    0: { color: '#cb181d'},
    1: { color: '3bad5e'},
    2: { color: 'yellow'},
    3: { color: '#ffffff'},
    4: { color: '#ffbf82'},
    5: { color: '#fff3b1'},
    6: { color: '#2d3639'},
    7: { color: '#397df9'},

  }
};

var Chart_area = ui.Chart.image.byClass({
  image: ee.Image.pixelArea().addBands(classified),
  classBand: 'classification', 
  region: point.buffer(10000),
  scale: 20,
  reducer: ee.Reducer.sum()
}).setOptions(options)
  .setSeriesNames(['Buld-up', 'Crop','FloodV','Grass','Scrub','Soil','Tree', 'water']);


print('charting by area ', Chart_area)






// visualization of the crop layer 

Map.addLayer(classified.clip(regions), mapViz, "LULCC")

var crops_class=classified.eq(1).clip(box)



var vectors_crops=crops_class.reduceToVectors({
  geometry:box,
  scale:50,
  geometryType:'polygon',
  maxPixels:1e13,
 
})


Map.addLayer(vectors_crops,{},'vectors tree')


Export.table.toDrive({
		collection:vectors_crops,
		description:'vectors_crops',
		folder:'Exemple_CSE_shp',
		fileFormat:'SHP',
})



// // // // //:::::::::::::::::::::::::::::::::  Visulization ::::::::::::::::::::::::::::::::::::::::::::::::::::


var C1=classified.eq(1).selfMask()
Map.addLayer(C1,{min:1,max:1, palette:'green'},'crops class')


//****************************************************** ex************************


  var palette= [
    '#cb181d',// building, (0)
    '3bad5e',// crops, (1)
    'yellow',// flooded vegetation, (2)
    '#ffffff',// Grass, (3)
    '#ffbf82',// scrub, (4)
    '#fff3b1',// soil, (5)
    '#2d3639',// tree, (6)
    '#397df9',// water, (7)
  ]

/*******************************************************************************************************l
* legend
* ****************************************************************************************************/

var labels=[
    "Building",
    "Crop",
    "Flooded Vegetation",
    "Grass",
    "Scrub",
    "Soil",
    "Tree",
    "Water"
  ]
var add_legend = function(title, lbl, pal) {
  var legend = ui.Panel({
      style: {
        position: 'bottom-right'
      }
    }),
    entry;
  legend.add(ui.Label({
    style: {
      fontWeight: 'bold',
      fontSize: '20px',
      margin: '1px 1px 4px 1px',
      padding: '2px'
    }
  }));
  for (var x = 0; x < lbl.length; x++) {
    entry = [ui.Label({
        style: {
          color: pal[x],
          border: '1px solid black',
          margin: '1px 1px 4px 1px'
        },
        value: '██'
      }),
      ui.Label({
        value: labels[x],
        style: {
          margin: '1px 1px 4px 4px'
        }
      })
    ];
    legend.add(ui.Panel(entry, ui.Panel.Layout.Flow('horizontal')));
  }
  Map.add(legend);
};
add_legend('Legend', labels, palette);


