# -*- coding: utf-8 -*-
"""
Created on Wed Apr 01 10:40:17 2015

@author: VHASLCShaoY
"""

import re
import random
import xlwt

def hasTask(task):
    tasks = [5.5]
    if task in tasks:
        print
        print '-'*10, 'Task:', task, '-'*10
        return True
    return False

debug = 0
def infoprint(s):
    if debug:
        print s

pdrive = r'\\vhacdwfpcfs02\Projects'
mainDir = pdrive + r'\ORD_Walsh_201201003D\Shao\Spondy'

if hasTask(-1):
    '''global variables for debugging'''
    idTokenStr2idSegment = {}
    idInit2idTokenStr = {}
    idRegEx2idInit = {}
    lists = {'inits':[],'actions':[]}
    idSnippet2pred_idRegExSpanPN = {}
    
def tokenizeSegments(segments):
    print 'tokenizing...'
    idTokenStr2idSegment.clear()
    maxSpaceLen = maxWordLen = maxNumberLen = 0
    p_token = re.compile(r'\w+|\s+|[^\w\s]')
    p_space = re.compile(r'\s+$')
    p_number = re.compile(r'\d+$')
    p_word = re.compile(r'\w+$')
    word2tf = {}
    uniqueSegs2idTokenString = {}
    tokenStrings = []
    sid = -1
    for segment in segments:
        sid += 1
        newseg = re.sub(p_space,' ',segment.lower())
        if newseg in uniqueSegs2idTokenString:
            idTokenStr2idSegment[uniqueSegs2idTokenString[newseg]].append(sid)
            continue
        uniqueSegs2idTokenString[newseg] = len(tokenStrings)
        tokens = [] 
        tokentypes = [] #0: space, 1: number, 2: word, 3: punct
        for token in re.findall(p_token, segment.lower()):
            if re.match(p_space, token):
                tokentypes.append(0)
                if maxSpaceLen < len(token):
                    maxSpaceLen = len(token)
            elif re.match(p_number, token):
                tokentypes.append(1)
                if maxNumberLen < len(token):
                    maxNumberLen = len(token)
            elif re.match(p_word, token):
                m = re.match(r'\d+',token)
                if m:
                    tokens.append(token[:m.end()])
                    tokentypes.append(1)
                    token = token[m.end():]
                tokentypes.append(2)
                if maxWordLen < len(token):
                    maxWordLen = len(token)
                word2tf[token] = word2tf.get(token,0)+1
            else:
                if ord(token)>127:
                    print token,
                tokentypes.append(3)
                token = '\\'+token
                word2tf[token] = word2tf.get(token,0)+1
            tokens.append(token)
        idTokenStr2idSegment[len(tokenStrings)] = [sid]
        tokenStrings.append((tokens,tokentypes))
    return tokenStrings,word2tf,maxSpaceLen,maxNumberLen,maxWordLen
    
def abstractSpaceDigits(tokens,tokentypes,maxSpaceLen,maxNumberLen):
    words = set()
    newtokens = []
    for i,token in enumerate(tokens):
        tokentype = tokentypes[i]
        if tokentype==0:
            newtokens.append(r'\s{1,%d}' %maxSpaceLen)
        elif tokentype==1:
            newtokens.append(r'\d{1,%d}' %maxNumberLen)
        else:
            newtokens.append(token)
        if tokentype in (2,3):
                words.add(token)
    return newtokens,words
    
def abstractAndTrim(tokenStrings,word2tf,maxSpaceLen,maxNumberLen,maxWordLen,oppSnippets):
    print 'abstracting and trimming...'
    idRegEx2idInit.clear()
    idInit2idTokenStr.clear()
    lists['inits'] = []
    lists['actions'] = []
    regExs = []
    uniqueRegExs2idRegEx = {}
    uniqueInits2idInit = {}
    progress = 0
    tid = -1
    for tokens, tokentypes in tokenStrings:
        tid += 1
        infoprint('----- token string -----')
        infoprint(''.join(tokens))
        infoprint('----- end of token string -----')
        progress = tid+1
        if progress%10==0:
            print '.',
        if progress%200==0:
            print progress
        newtokens,words = abstractSpaceDigits(tokens,tokentypes,maxSpaceLen,maxNumberLen)
        regEx = ''.join(newtokens)
        if regEx in uniqueInits2idInit:
            idInit2idTokenStr[uniqueInits2idInit[regEx]].append(tid)
            continue
        matched = False
        for k,snippet in enumerate(oppSnippets):
            m = re.search(regEx,snippet,re.I)
            if m:
                matched = True
                break # for k, snippet
        if matched:
            continue # for tokens, tokentypes in
        uniqueInits2idInit[regEx] = len(lists['inits'])
        idInit2idTokenStr[len(lists['inits'])] = [tid]
        lists['inits'].append(regEx)
        actions = []
        currTokens = newtokens
        currTypes = tokentypes
        matched = False
        leftTrimMatched = False
        rightTrimMatched = False
        for w in sorted(words,key=lambda w:(word2tf[w],len(w),w)):
            newTokens = []
            newTypes = []
            i = 0
            while i<len(currTokens):
                if currTypes[i]==2 and currTokens[i]==w:
                    if i+2<len(currTokens) and len(newTokens)>0 and newTypes[-1]==14 and currTypes[i+1]==0 and currTypes[i+2]==14:
                        repeatLen = int(newTokens[-1].split(',')[-1][:-1])
                        repeatLen += int(currTokens[i+2].split(',')[-1][:-1])
                        newToken = r'(?:[a-z]\w{0,%d}\s{1,%d}){1,%d}' %(maxWordLen-1,maxSpaceLen,repeatLen+1)
                        newType = 14
                        del newTokens[-1]
                        del newTypes[-1]
                        i += 2
                    elif i+2<len(currTokens) and currTypes[i+1]==0 and currTypes[i+2]==14:
                        repeatLen = int(currTokens[i+2].split(',')[-1][:-1])
                        newToken = r'(?:[a-z]\w{0,%d}\s{1,%d}){1,%d}' %(maxWordLen-1,maxSpaceLen,repeatLen+1)
                        newType = 14
                        i += 2
                    elif i+1<len(currTokens) and len(newTokens)>0 and newTypes[-1]==14 and currTypes[i+1]==0:
                        repeatLen = int(newTokens[-1].split(',')[-1][:-1])
                        newToken = r'(?:[a-z]\w{0,%d}\s{1,%d}){1,%d}' %(maxWordLen-1,maxSpaceLen,repeatLen+1)
                        newType = 14
                        del newTokens[-1]
                        del newTypes[-1]
                        i += 1
                    elif i+1<len(currTokens) and currTypes[i+1]==0:
                        newToken = r'(?:[a-z]\w{0,%d}\s{1,%d}){1,1}' %(maxWordLen-1,maxSpaceLen)
                        newType = 14
                        i += 1
                    else:
                        newToken=r'[a-z]\w{0,%d}' %(maxWordLen-1)
                        newType = 12
                    newTokens.append(newToken)
                    newTypes.append(newType)
                elif currTypes[i]==3 and currTokens[i]==w:
                    newToken=r'[^\w\s]'
                    newType = 13
                    newTokens.append(newToken)
                    newTypes.append(newType)
                else:
                    newTokens.append(currTokens[i])
                    newTypes.append(currTypes[i])
                i += 1
            regEx = ''.join(newTokens)
            if True:
                infoprint('----- abstract: '+w+' -----')
                infoprint(regEx)
                infoprint(newTokens)
                infoprint(newTypes)
            for snippet in oppSnippets:
                m = re.search(regEx,snippet,re.I)
                if m:
                    matched = True
                    break # for snippet (4 lines above)
            if True:
                infoprint(matched)
            if matched:
                actions.append(w+':F')
                break # for w in
            actions.append(w+':S')
            currTokens = newTokens
            currTypes = newTypes
            if not leftTrimMatched and (newTypes[0]<=1 or newTypes[0]>=10):
                regEx = ''.join(newTokens[1:])
                for snippet in oppSnippets:
                    m = re.search(regEx,snippet,re.I)
                    if m:
                        actions.append('left-trim:F')
                        leftTrimMatched = True
                        break # for snippet (4 lines above)
                if not leftTrimMatched:
                    actions.append('left-trim:S')
                    newTokens = newTokens[1:]
                    newTypes = newTypes[1:]
            if not rightTrimMatched and (newTypes[-1]<=1 or newTypes[-1]>=10):
                regEx = ''.join(newTokens[:-1])
                for snippet in oppSnippets:
                    m = re.search(regEx,snippet,re.I)
                    if m:
                        actions.append('right-trim:F')
                        rightTrimMatched = True
                        break # for snippet (4 lines above)
                if not rightTrimMatched:
                    actions.append('right-trim:S')
                    newTokens = newTokens[:-1]
                    newTypes = newTypes[:-1]
            currTokens = newTokens
            currTypes = newTypes
        leftTrimStop = leftTrimMatched
        rightTrimStop = rightTrimMatched
        n = 0
        while (not leftTrimStop) or (not rightTrimStop):
            n += 1
            if n>100:
                break
            if not leftTrimStop: 
                if (currTypes[0]<=1 or currTypes[0]>=10):
                    regEx = ''.join(currTokens[1:])
                    for snippet in oppSnippets:
                        m = re.search(regEx,snippet,re.I)
                        if m:
                            actions.append('left-trim:F')
                            leftTrimMatched = True
                            leftTrimStop = True
                            break # while (not leftTrimStop) or (not rightTrimStop):
                    if not leftTrimMatched:
                        actions.append('left-trim:S')
                        currTokens = currTokens[1:]
                        currTypes = currTypes[1:]
                else:
                    leftTrimStop = True
            if not rightTrimStop:
                if (currTypes[-1]<=1 or currTypes[-1]>=10):
                    regEx = ''.join(currTokens[:-1])
                    for snippet in oppSnippets:
                        m = re.search(regEx,snippet,re.I)
                        if m:
                            actions.append('right-trim:F')
                            rightTrimMatched = True
                            rightTrimStop = True
                            break # while (not leftTrimStop) or (not rightTrimStop):
                    if not rightTrimMatched:
                        actions.append('right-trim:S')
                        currTokens = currTokens[:-1]
                        currTypes = currTypes[:-1]
                else:
                    rightTrimStop = True
        lists['actions'].append(actions)
        finalRegEx = ''.join(currTokens)
        if finalRegEx in uniqueRegExs2idRegEx:
            idRegEx2idInit[uniqueRegExs2idRegEx[finalRegEx]].append(len(lists['inits'])-1)
            continue
        uniqueRegExs2idRegEx[finalRegEx] = len(regExs)
        idRegEx2idInit[len(regExs)] = [len(lists['inits'])-1]
        regExs.append(finalRegEx)
    if progress%100!=0:
        print progress
    return regExs

def calcSensitivity(regExs,snippets):
    print 'calculating sensitivity...'
    sens = []
    counts = []
    for regEx in regExs:
        count = 0
        for snippet in snippets:
            m = re.search(regEx,snippet,re.I)
            if m:
                count += 1
        counts.append(count)
        sens.append(count*100.0/len(snippets))
    return sens,counts
    
def generateRegExs(segments,snippets,oppSnippets):
    tokenStrings,word2tf,maxSpaceLen,maxNumberLen,maxWordLen = tokenizeSegments(segments)
    regExs = abstractAndTrim(tokenStrings,word2tf,maxSpaceLen,maxNumberLen,maxSpaceLen,oppSnippets)
    sens,counts = calcSensitivity(regExs,snippets)
    return regExs,sens
    
def saveRegExsToFile(regExs,segments,fname):
    with open(fname,'w') as f:
        for i1 in idRegEx2idInit:
            f.write(str(i1+1)+' '+regExs[i1]+'\n')
            for i2 in idRegEx2idInit[i1]:
                f.write('\t'+' '.join(lists['actions'][i2])+'\n')
                f.write('\t'+lists['inits'][i2]+'\n')
                for i3 in idInit2idTokenStr[i2]:
                    i4 = idTokenStr2idSegment[i3][0]
                    f.write('\t\t'+re.sub(r'\n',' ',segments[i4])+'\n')
    
def predict(snippets,posRegExs,posSens,negRegExs,negSens):
    idSnippet2pred_idRegExSpanPN.clear()
    idRegExSpanPN = []
    print 'predicting...'
    posIdxSorted = sorted(range(len(posSens)),key=lambda i:-posSens[i])
    negIdxSorted = sorted(range(len(negSens)),key=lambda i:-negSens[i])
    idxSnippet2posSen = {}
    idxSnippet2negSen = {}
    idxSnippet2idPosRegExSpan = {}
    idxSnippet2idNegRegExSpan = {}
    for i in posIdxSorted:
        pattern = re.compile(posRegExs[i],re.I)
        for j, snippet in enumerate(snippets):
            if j in idxSnippet2posSen:
                continue
            m = re.search(pattern,snippet)
            if m:
                idxSnippet2posSen[j]=posSens[i]
                idxSnippet2idPosRegExSpan[j] = (i,m.start(),m.end())
    for i in negIdxSorted:
        pattern = re.compile(negRegExs[i],re.I)
        for j, snippet in enumerate(snippets):
            if j in idxSnippet2negSen:
                continue
            m = re.search(pattern,snippet)
            if m:
                idxSnippet2negSen[j]=negSens[i]
                idxSnippet2idNegRegExSpan[j] = (i,m.start(),m.end())
    predictions = []
    for j, snippet in enumerate(snippets):
        posSen = idxSnippet2posSen.get(j,0)
        negSen = idxSnippet2negSen.get(j,0)
        pred = -1
        if posSen==negSen==0:
            pred = 0
        elif posSen>negSen:
            pred = 1
        predictions.append(pred)
        idRegExSpanPN.append((idxSnippet2idPosRegExSpan.get(j,None),idxSnippet2idNegRegExSpan.get(j,None)))
    return predictions,idRegExSpanPN

if hasTask(0):
    vttFile = r'\spond_1500annotate_Tobee_12-29-14.xlsx.vtt'
    rawdata = []
    markUpStart = -1
    n = -1
    with open(mainDir +vttFile) as f:
        lines = f.readlines()
    nLines = len(lines)
    textStart, textEnd, markUpStart = -1, -1, -1
    sepLines = []
    labels = {}
    label, start, end = '', -1, -1
    snippetStart, snippetEnd = 0, 0
    textSeparator = '-'*82
    for n in xrange(nLines):
        if textStart >0 and textEnd<0 and lines[n].startswith('#<-----'):
            textEnd = n
            sepLines.append(n+1)
        if textStart==0 and lines[n].startswith('#<-----'):
            textStart = n+1
            sepLines.append(n)
        if textStart<0 and lines[n].startswith('#<Text Content>'):
            textStart = 0
        if textStart >0 and textEnd <0 and lines[n].startswith(textSeparator):
            sepLines.append(n)
        if markUpStart>0:
            items = lines[n].split('|')
            if items[4]:
                subitems = items[4].split('"')
                snippetID = int(subitems[1])
                if subitems[5]=='Snippet Text':
                    snippetStart = int(items[0].strip())
            if not items[2].startswith('SnippetColumn'):
                label = items[2].strip()
                start = int(items[0].strip())
                end = start+int(items[1].strip())
                relStart = start-snippetStart
                relEnd = relStart +int(items[1].strip())
                labels[snippetID] = (label,start,end,relStart,relEnd)
        if markUpStart==0 and lines[n].startswith('#<-----'):
            markUpStart = n+1
        if markUpStart<0 and lines[n].startswith('#<MarkUps Information>'):
            markUpStart = 0
    pos = 0
    for i in xrange(len(sepLines)-1):
        snippet = ''.join(lines[sepLines[i]+6:sepLines[i+1]-1])
        pos += len(''.join(lines[sepLines[i]+1:sepLines[i]+6]))
        if i+1 in labels:
            rawdata.append((labels[i+1][0],labels[i+1][3],labels[i+1][4],snippet))
        pos += len(snippet)+len(lines[sepLines[i+1]])+1
        
if hasTask(1):
    snippetsNP = [[],[]]
    segmentsNP = [[],[]]
    for label,start,end,snippet in rawdata:
        idxNP = int(label=='Yes')
        k = -1
        if snippet.startswith('(#'):
            k = snippet.find(')')
        snippetsNP[idxNP].append(snippet[k+1:])
        segment = snippet[start:end].strip()
        segmentsNP[idxNP].append(segment)

if hasTask(1.5):
    tokenStrings,word2tf,maxSpaceLen,maxNumberLen,maxWordLen = tokenizeSegments(segmentsNP[1])
    #regExs = abstractAndTrim(tokenStrings[44:45],word2tf,maxSpaceLen,maxDigitLen,maxSpaceLen,snippetsNP[0])
    #sens,counts = calcSensitivity(regExs,snippetsNP[1])
    regExs = abstractAndTrim(tokenStrings[:20],word2tf,maxSpaceLen,maxNumberLen,maxSpaceLen,snippetsNP[0])
    #sens,counts = calcSensitivity(regExs,snippetsNP[1])

if hasTask(2):
    posRegExs,posSens = generateRegExs(segmentsNP[1],snippetsNP[1],snippetsNP[0])
    
if hasTask(2.5):
    for i in xrange(10):
        print '%.2f\t%s' %(posSens[i],posRegExs[i])

if hasTask(3):
    negRegExs,negSens = generateRegExs(segmentsNP[0],snippetsNP[0],snippetsNP[1])
        
if hasTask(3.5):
    for i in xrange(10):
        print '%.2f\t%s' %(negSens[i],negRegExs[i])
        
if hasTask(4):
    preds = predict(snippetsNP[1],posRegExs,posSens,negRegExs,negSens,0)
    accuracy = sum(preds)*1.0/len(preds)
    print accuracy
    
if hasTask(5):
    posIdxs = range(len(segmentsNP[1]))
    negIdxs = range(len(segmentsNP[0]))
    random.seed(1)
    random.shuffle(posIdxs)
    random.shuffle(negIdxs)
    posTenFolds = []
    q = len(posIdxs)/10
    r = len(posIdxs)%10
    start = end = 0
    for i in xrange(10):
        end += q+(i<r)
        posTenFolds.append(posIdxs[start:end])
        start = end
    negTenFolds = []
    q = len(negIdxs)/10
    r = len(negIdxs)%10
    start = end = 0
    for i in xrange(10):
        end += q+(i<r)
        negTenFolds.append(negIdxs[start:end])
        start = end
    allPosPreds = []
    allNegPreds = []
    for k in xrange(10):
        print 'Fold:', k+1
        posSnippetsTrain = []
        posSegmentsTrain = []
        posSnippetsTest = []
        posSegmentsTest = []
        for j in xrange(10):
            for i in posTenFolds[j]:
                if j==k:
                    posSnippetsTest.append(snippetsNP[1][i])
                    posSegmentsTest.append(segmentsNP[1][i])
                else:
                    posSnippetsTrain.append(snippetsNP[1][i])
                    posSegmentsTrain.append(segmentsNP[1][i])
        negSnippetsTrain = []
        negSegmentsTrain = []
        negSnippetsTest = []
        negSegmentsTest = []
        for j in xrange(10):
            for i in negTenFolds[j]:
                if j==k:
                    negSnippetsTest.append(snippetsNP[0][i])
                    negSegmentsTest.append(segmentsNP[0][i])
                else:
                    negSnippetsTrain.append(snippetsNP[0][i])
                    negSegmentsTrain.append(segmentsNP[0][i])
        print 'Training...'
        posRegExs, posSens = generateRegExs(posSegmentsTrain,posSnippetsTrain,negSnippetsTrain)
        #saveRegExsToFile(posRegExs,posSegmentsTrain,mainDir+r'\NewRED\Fold9-RegExs-Pos.txt')
        negRegExs, negSens = generateRegExs(negSegmentsTrain,negSnippetsTrain,posSnippetsTrain)
        #saveRegExsToFile(negRegExs,negSegmentsTrain,mainDir+r'\NewRED\Fold9-RegExs-Neg.txt')
        print '# of pos regexs =', len(posSens)
        print '# of neg regexs =', len(negSens)
        print 'Testing...'
        posPreds,pIdRegExSpanPN = predict(posSnippetsTest,posRegExs,posSens,negRegExs,negSens)
        negPreds,nIdRegExSpanPN = predict(negSnippetsTest,posRegExs,posSens,negRegExs,negSens)
        PP,PN,PU = posPreds.count(1),posPreds.count(-1),posPreds.count(0) 
        NP,NN,NU = negPreds.count(1),negPreds.count(-1),negPreds.count(0)
        print '\tPrediction\n\tP\tN\tU\tTotal'
        print 'Actual P\t%d\t%d\t%d\t%d' %(PP,PN,PU,PP+PN+PU)
        print 'Actual N\t%d\t%d\t%d\t%d' %(NP,NN,NU,NP+NN+NU)
        print 'If default = positive:'
        sensitivity = (PP+PU)*1.0/len(posPreds)
        specificity = NN*1.0/len(negPreds)
        accuracy = (PP+PU+NN)*1.0/(len(posPreds)+len(negPreds))
        print 'Sensitivity = %.3f, Specificity = %.3f, Accuracy = %.3f' %(sensitivity,specificity,accuracy)
        print 'If default = negative:'
        sensitivity = PP*1.0/len(posPreds)
        specificity = (NN+NU)*1.0/len(negPreds)
        accuracy = (PP+NU+NN)*1.0/(len(posPreds)+len(negPreds))
        print 'Sensitivity = %.3f, Specificity = %.3f, Accuracy = %.3f' %(sensitivity,specificity,accuracy)
        print
        allPosPreds += posPreds
        allNegPreds += negPreds
        
if hasTask(5.5):
    aPP,aPN,aPU = allPosPreds.count(1),allPosPreds.count(-1),allPosPreds.count(0) 
    aNP,aNN,aNU = allNegPreds.count(1),allNegPreds.count(-1),allNegPreds.count(0)
    print 'Overall on 10 folds combined:'
    print '\tPrediction\n\tP\tN\tU\tTotal'
    print 'Actual P\t%d\t%d\t%d\t%d' %(aPP,aPN,aPU,len(allPosPreds))
    print 'Actual N\t%d\t%d\t%d\t%d' %(aNP,aNN,aNU,len(allNegPreds))
    print 'If default = positive:'
    sensitivity = (aPP+aPU)*1.0/len(allPosPreds)
    specificity = aNN*1.0/len(allNegPreds)
    accuracy = (aPP+aPU+aNN)*1.0/(len(allPosPreds)+len(allNegPreds))
    print 'Sensitivity = %.3f, Specificity = %.3f, Accuracy = %.3f' %(sensitivity,specificity,accuracy)
    print 'If default = negative:'
    sensitivity = aPP*1.0/len(allPosPreds)
    specificity = (aNN+aNU)*1.0/len(allNegPreds)
    accuracy = (aPP+aNU+aNN)*1.0/(len(allPosPreds)+len(allNegPreds))
    print 'Sensitivity = %.3f, Specificity = %.3f, Accuracy = %.3f' %(sensitivity,specificity,accuracy)

if hasTask(6):
    book = xlwt.Workbook()
    sheet = book.add_sheet('Positive Snippets')
    headers = ('Snippet','Labeled Segment','Prediction',
    'Positive RegEx','Sensitivity', 'Segment Matched by Pos RegEx',
    'Negative RegEx', 'Sensitivity', 'Segment Matched by Neg RegEx')
    for j,header in enumerate(headers):
        sheet.write(0,j,header)
    for i,snippet in enumerate(posSnippetsTest):
        segment = posSegmentsTest[i]
        if pIdRegExSpanPN[i][0]:
            k,start,end = pIdRegExSpanPN[i][0]
            posRegEx = str(k+1)+' '+posRegExs[k]
            posSen = '%.1f' %posSens[k]
            posMatch = snippet[start:end]
        else:
            posRegEx = posSen = posMatch = ''
        if pIdRegExSpanPN[i][1]:
            k,start,end = pIdRegExSpanPN[i][1]
            negRegEx = str(k+1)+' '+negRegExs[k]
            negSen = '%.1f' %negSens[k]
            negMatch = snippet[start:end]
        else:
            negRegEx = negSen = negMatch = ''
        pred = posPreds[i]
        if pred==1:
            pred2 = 'P'
        elif pred==-1:
            pred2 = 'N'
        else:
            pred2 = 'U'
        items = (snippet,segment,pred2,posRegEx,posSen,posMatch,negRegEx,negSen,negMatch,)
        for j,item in enumerate(items):
            sheet.write(i+1,j,item)
    sheet = book.add_sheet('Negative Snippets')
    headers = ('Snippet','Labeled Segment','Prediction',
    'Positive RegEx','Sensitivity', 'Segment Matched by Pos RegEx',
    'Negative RegEx', 'Sensitivity', 'Segment Matched by Neg RegEx')
    for j,header in enumerate(headers):
        sheet.write(0,j,header)
    for i,snippet in enumerate(negSnippetsTest):
        segment = negSegmentsTest[i]
        if nIdRegExSpanPN[i][0]:
            k,start,end = nIdRegExSpanPN[i][0]
            posRegEx = str(k+1)+') '+posRegExs[k]
            posSen = '%.1f' %posSens[k]
            posMatch = snippet[start:end]
        else:
            posRegEx = posSen = posMatch = ''
        if nIdRegExSpanPN[i][1]:
            k,start,end = nIdRegExSpanPN[i][1]
            negRegEx = str(k+1)+') '+negRegExs[k]
            negSen = '%.1f' %negSens[k]
            negMatch = snippet[start:end]
        else:
            negRegEx = negSen = negMatch = ''
        pred = negPreds[i]
        if pred==1:
            pred2 = 'P'
        elif pred==-1:
            pred2 = 'N'
        else:
            pred2 = 'U'
        items = (snippet,segment,pred2,posRegEx,posSen,posMatch,negRegEx,negSen,negMatch,)
        for j,item in enumerate(items):
            sheet.write(i+1,j,item)
    book.save(mainDir+r'\NewRED\Fold9 Predictions.xls')