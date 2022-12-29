from tensorflow.keras.datasets import mnist
from tensorflow.keras.utils import to_categorical
from tensorflow.keras.layers import Conv2D, BatchNormalization, MaxPooling2D, Dropout
from tensorflow.keras.layers import Flatten, Dense
from tensorflow.keras.models import Sequential
from tensorflow.keras.callbacks import EarlyStopping
from tensorflow import lite

from keras.preprocessing.image import ImageDataGenerator
import pickle
import numpy as np 

M = N = 14

def split(img):
    return [img[x : x + M, y : y + N] for x in range(0, img.shape[0], M) for y in range(0, img.shape[1],N)]

def split_images(X, Y):
    updated_X = [[], [], [], []]
    updated_Y = [[], [], [], []]
    for x, y in zip(X, Y):
        tiles = split(x)
        for i, img in enumerate(tiles, 0):
            updated_X[i].append(img)
            updated_Y[i].append(y)

    for i in range(len(updated_X)):
        updated_X[i] = np.array(updated_X[i])
        updated_Y[i] = np.array(updated_Y[i])

        updated_X[i] = updated_X[i].reshape((updated_X[i].shape[0], 14, 14, 1)) / 255.0
        updated_Y[i] = to_categorical(updated_Y[i])
    
    return updated_X, updated_Y

def preprocess_data():
    (trainX, trainY), (testX, testY) = mnist.load_data()
    trainX, trainY = split_images(trainX, trainY)
    testX, testY = split_images(testX, testY)
    return trainX, testX, trainY, testY

def define_model():
    cnn_model=Sequential()
    cnn_model.add(Conv2D(32, (3,3) , activation='relu', input_shape=(14,14,1))) 
    cnn_model.add(BatchNormalization())
    
    cnn_model.add(Conv2D(32, (3,3) , activation='relu'))
    cnn_model.add(BatchNormalization())
    cnn_model.add(MaxPooling2D((2, 2),padding='same'))
    cnn_model.add(Dropout(0.25))

    cnn_model.add(Conv2D(64, (3,3) , activation='relu'))
    cnn_model.add(BatchNormalization())
    cnn_model.add(Dropout(0.25))

    cnn_model.add(Conv2D(128, (3,3) , activation='relu'))
    cnn_model.add(BatchNormalization())
    cnn_model.add(MaxPooling2D((2, 2),padding='same'))
    cnn_model.add(Dropout(0.25))

    cnn_model.add(Flatten())
    cnn_model.add(Dense(512, activation='relu'))
    cnn_model.add(BatchNormalization())
    cnn_model.add(Dropout(0.5))
	
    cnn_model.add(Flatten())
    cnn_model.add(Dense(128, activation='relu'))
    cnn_model.add(BatchNormalization())
    cnn_model.add(Dropout(0.5))
	
    cnn_model.add(Dense(10, activation='softmax'))

    cnn_model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])
    return cnn_model

def convert_and_save(model, index):
    converter = lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    with open(f'model_{index}.tflite', 'wb') as f:
        f.write(tflite_model)
    
def train_model(trainX, testX, trainY, testY, index):
    print(trainX[0].shape)
    print(trainY[0].shape)
    cnn_model = define_model()
    early_stopper = EarlyStopping(monitor='loss', patience=3, restore_best_weights=True) 
    his = cnn_model.fit(trainX, trainY, steps_per_epoch=175, 
                                epochs=20, validation_steps=20, 
                                callbacks=[early_stopper],
                                verbose = 1)
    _, acc = cnn_model.evaluate(testX, testY, verbose=1)
    filename = f'./new_model_{index}.sav'
    pickle.dump(cnn_model, open(filename, 'wb'))
    convert_and_save(cnn_model, index)
    return his, acc

if __name__ == '__main__':
    trainX, testX, trainY, testY = preprocess_data()
    for index in range(4):

        his, test_acc = train_model(trainX[index], testX[index], trainY[index], testY[index], index+1)
        print(f"Test Accuracy : {test_acc} \nHistory : {his} for [{index}]")
    