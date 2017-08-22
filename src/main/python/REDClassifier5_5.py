# -*- coding: utf-8 -*-
"""
Created on Mon Mar 28 10:51:46 2016

@author: vhaslcshaoy
"""

import re
from math import log
from gov.va.research.red.cat import IREDClassifier
from java.util import ArrayList

class REDClassifier(IREDClassifier):
    def __init__(self):
        self._cls2snippets_segspans = {}
        self._cls2maxSpaceLen = {}
        self._cls2maxNumberLen = {}
        self._cls2maxWordLen = {}
        self._cls2wordpun2tfdf = {}
        self._cls2wordpunSorted = {}
        self._cls2segments = {}
        self._cls2idSeg2idSnippets = {}
        self._cls2initTokenstrs = {}
        self._cls2initRejected = {}
        self._cls2rejected_cls2count = {}
        self._cls2initActions = {}
        self._cls2idInit2idSegs = {}
        self._cls2strictTokenstrs = {}
        self._cls2idStrict2idInits = {}
        self._cls2strictCounts = {}
        self._cls2idStrictSorted = {}
        self._cls2overTokenstrs = {}
        self._cls2strictOverFailed = {}
        self._cls2strictActions = {}
        self._cls2idOver2idStricts = {}
        self._cls2idOver2cls2count = {}
        self._clsIdOverSorted = []
        self._cls2over30Tokenstrs = {}
        self._cls2idOver302cls2count = {}
        self._cls2over31Tokenstrs = {}
        self._cls2idOver312cls2count = {}
        self._clsIdOver30Sorted = []
        self._cls2idOver302idStricts = {}
        self._remodule = re
        
    def fit(self,snippets,segspans,labels):
        if self._cls2snippets_segspans:
            self.__init__()
        #print 'preprocessing...'
        self._preprocess(snippets,segspans,labels)
        #print 'initializing...'
        self._initialize()
        self._sortWordpun()
        print 'generating strict regular expressions'
        self._generalize0()
        self._calcCounts()
        self._sortStrict()
        print 'generating less strict regular expressions'
        self._generalize1()
        self._sortOver()
        print 'generating least strict regular expressions'
        self._generalize30()
        self._sortOver30()
        
    def predict(self,snippets,labelForUndecided):
        print 'predicting...'
        preds0 = self._predict0(snippets)
        preds1 = self._predict1(snippets)
        preds3 = self._predict3(snippets)
        preds = []
        for i in xrange(len(snippets)):
            pred0 = preds0[i]
            pred1 = preds1[i]
            pred3 = preds3[i]
            if pred0 is not None:
                pred = pred0
            elif pred1 is not None:
                pred = pred1
            elif pred3 is not None:
                pred = pred3
            else:
                pred = labelForUndecided
            preds.append(pred)
        return preds
        
    def getStrictRegexs(self,label):
        regexs = []
        for i in self._cls2idStrictSorted[label]:
            regex = self._getRegex(self._cls2strictTokenstrs[label][i])
            regexs.append(regex)
        return regexs
        
    def getLessStrictRegexs(self,label):
        regexs = []
        for cls,i in self._clsIdOverSorted:
            if cls!=label:
                continue
            regex = self._getRegex(self._cls2overTokenstrs[cls][i])
            regexs.append(regex)
        return regexs
        
    def getLeastStrictRegexs(self,label):
        regexs = []
        for cls,i in self._clsIdOver30Sorted:
            if cls!=label:
                continue
            regex = self._getRegex(self._cls2over30Tokenstrs[cls][i])
            regexs.append(regex)
        return regexs
    
    def _preprocess(self,snippets,segspans,labels):
        def fixSpan(snippet,start,end):
            if start>0 and snippet[start-1].isalnum() and snippet[start].isalnum():
                start -= 1
                while start>0 and snippet[start-1].isalnum():
                    start -=1
            if end<len(snippet) and snippet[end-1].isalnum() and snippet[end].isalnum():
                while end<len(snippet) and snippet[end].isalnum():
                    end += 1
            return (snippet,start,end)
        assert isinstance(snippets,ArrayList)
        assert isinstance(segspans,ArrayList)
        assert isinstance(labels,ArrayList)
        assert len(snippets)==len(segspans)==len(labels)
        for i,cls in enumerate(labels):
            if not cls in self._cls2snippets_segspans:
                self._cls2snippets_segspans[cls] = []
            snippet,(start,end) = snippets[i],segspans[i]
            self._cls2snippets_segspans[cls].append(fixSpan(snippet,start,end))

    def _initialize(self):
        '''tokennize, initial abstraction and word count'''
        p_token = re.compile(r'\w+|\s+|[^\w\s]')
        p_spaceE = re.compile(r'\s+$')
        p_numberE = re.compile(r'\d+$')
        p_wordE = re.compile(r'\w+$')
        p_space = re.compile(r'\s+')
        p_number = re.compile(r'\d+')
        for cls in self._cls2snippets_segspans:
            segments = []
            unqSeg2idSeg = {}
            idSeg2idSnips = {}
            tokenstrs = []
            wordpun2tfdf = {}
            maxSpaceLen = maxNumberLen = maxWordLen = 0
            for i,(snippet,start,end) in enumerate(self._cls2snippets_segspans[cls]):
                seg = snippet[start:end].strip().lower()
                tokens,ttypes = [],[]
                for token in re.findall(p_token, seg):
                    if re.match(p_spaceE, token):
                        ttypes.append(0)
                        if maxSpaceLen < len(token):
                            maxSpaceLen = len(token)
                    elif re.match(p_numberE, token):
                        ttypes.append(1)
                        if maxNumberLen < len(token):
                            maxNumberLen = len(token)
                    elif re.match(p_wordE, token):
                        m = re.match(p_number,token)
                        if m:
                            tokens.append(token[:m.end()])
                            ttypes.append(1)
                            if maxNumberLen < m.end():
                                maxNumberLen = m.end()
                            token = token[m.end():]
                        ttypes.append(2)
                        if maxWordLen < len(token):
                            maxWordLen = len(token)
                    else:
                        if ord(token)>127:
                            print token,
                        ttypes.append(3)
                        token = '\\'+token
                    tokens.append(token)
                    if ttypes[-1] in (2,3):
                        if not token in wordpun2tfdf:
                            wordpun2tfdf[token] = [0,0]
                        wordpun2tfdf[token][0] += 1
                nseg = re.sub(p_space,' ',seg)
                if nseg in unqSeg2idSeg:
                    idSeg2idSnips[unqSeg2idSeg[nseg]].append(i)
                    continue
                unqSeg2idSeg[nseg] = len(segments)
                idSeg2idSnips[len(segments)] = [i]
                segments.append(nseg)
                tokenstrs.append((tokens,ttypes))
            inits = []
            unqInit2idInit = {}
            idInit2idSegs = {}
            for idx,(tokens,ttypes) in enumerate(tokenstrs):
                for i,ttype in enumerate(ttypes):
                    if ttype==0:
                        tokens[i] = r'\s{1,%d}'%maxSpaceLen
                    elif ttype==1:
                        tokens[i] = r'\d{1,%d}'%maxNumberLen
                initRegex = self._getRegex((tokens,ttypes))
                if initRegex in unqInit2idInit:
                    idInit2idSegs[unqInit2idInit[initRegex]].append(idx)
                    continue
                idInit2idSegs[len(inits)] = [idx]
                unqInit2idInit[initRegex] = len(inits)
                inits.append((tokens,ttypes))
            for snippet,start,end in self._cls2snippets_segspans[cls]:
                wordpuns = set()
                snippet = snippet.lower()
                for token in re.findall(p_token,snippet[:start]+' '+snippet[end:]):
                    if re.match(p_spaceE, token):
                        continue
                    if re.match(p_numberE,token):
                        continue
                    if re.match(p_wordE, token):
                        m = re.match(p_number,token)
                        if m:
                            token = token[m.end():]
                    else:
                        token = '\\'+token
                    wordpuns.add(token)
                for wp in wordpuns:
                    if wp in wordpun2tfdf:
                        wordpun2tfdf[wp][1] += 1
            self._cls2maxSpaceLen[cls] = maxSpaceLen
            self._cls2maxNumberLen[cls] = maxNumberLen
            self._cls2maxWordLen[cls] = maxWordLen
            self._cls2wordpun2tfdf[cls] = wordpun2tfdf
            self._cls2segments[cls] = segments
            self._cls2idSeg2idSnippets[cls] = idSeg2idSnips
            self._cls2initTokenstrs[cls] = inits
            self._cls2idInit2idSegs[cls] = idInit2idSegs
            
    def _sortWordpun(self):
        def tfidf(tfdf):
            return tfdf[0]*(logNplus1-log(tfdf[1]+1))
        for cls in self._cls2wordpun2tfdf:
            logNplus1 = log(len(self._cls2snippets_segspans[cls])+1)
            wordpun2tfdf = self._cls2wordpun2tfdf[cls]
            wordSorted = sorted(wordpun2tfdf,key=lambda w:(tfidf(wordpun2tfdf[w]),len(w)))
            self._cls2wordpunSorted[cls] = wordSorted
            
    def _abstract(self,w,currTokens,currTypes,maxSpaceLen,maxWordLen):
        newTokens,newTypes = [],[]
        i = 0
        while i<len(currTokens):
            if currTokens[i]==w:
                if currTypes[i]==2:
                    if i+2<len(currTokens) and len(newTokens)>0 and newTypes[-1]==14 and currTypes[i+1]==0 and currTypes[i+2]==14:
                        repeatLen = int(newTokens[-1].split(',')[-1][:-1])
                        repeatLen += int(currTokens[i+2].split(',')[-1][:-1])
                        newToken = r'(?:[a-z]\w{0,%d}\s{1,%d}){1,%d}'%(maxWordLen-1,maxSpaceLen,repeatLen+1)
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
                elif currTypes[i]==3:
                    newToken=r'[^\w\s]'
                    newType = 13
                    newTokens.append(newToken)
                    newTypes.append(newType)
            else:
                newTokens.append(currTokens[i])
                newTypes.append(currTypes[i])
            i += 1
        return newTokens,newTypes
        
    def _nLeftTrim(self,currTypes):
        if currTypes[0]==12:
            if len(currTypes)==1:
                return 1
            elif currTypes[1]==0:
                return 2
            else:
                return 0
        if currTypes[0]<=1 or currTypes[0]>10:
            return 1
        return 0
            
    def _nRightTrim(self,currTypes):
        if currTypes[-1]==1 or currTypes[-1]>10:
            return 1
        if currTypes[-1]==0:
            if len(currTypes)==1:
                return 1
            if currTypes[-2]==12:
                return 2
            if currTypes[-2]==2:
                return 0
            else:
                return 1
        return 0
        
    def _getRegex(self,tokenstr):
        tokens,ttypes = tokenstr
        regex = ''.join(tokens)
        try:
            if ttypes[0] in (1,2,12,14):
                regex = r'\b'+regex
        except IndexError:
            print "ttypes does not have a 0 index"
        try:
            if ttypes[-1] in (1,2,12):
                regex = regex+r'\b'
        except IndexError:
            print "ttypes does not hava a -1 index"
        return regex
        
    def _viewRegex(self,tokenstr):
        tokens,ttypes = tokenstr
        regex = ''.join(tokens)
        regex = regex.replace(r'?:','')
        return regex
    
    def _generalize0(self):
        def matchOppOf(regex,acls):
            for cls in self._cls2snippets_segspans:
                if cls==acls:
                    continue
                for snippet,start,end in self._cls2snippets_segspans[cls]:
                    if re.search(regex,snippet,re.I):
                        return True
            return False
        for cls in self._cls2initTokenstrs:
            print 'for class:',cls
            self._cls2initRejected[cls] = []
            self._cls2initActions[cls] = []
            maxSpaceLen = self._cls2maxSpaceLen[cls]
            maxWordLen = self._cls2maxWordLen[cls]
            unqRegex2idStrict = {}
            strictTokenstrs = []
            idStrict2idInits = {}
            for idx,tokenstr in enumerate(self._cls2initTokenstrs[cls]):
                progress = idx+1
                if progress%10==0:
                    print '.',
                if progress%200==0:
                    print
                initregex = self._getRegex(tokenstr)
                actions = []
                if matchOppOf(initregex,cls):
                    self._cls2initRejected[cls].append(idx)
                    self._cls2initActions[cls].append(actions)
                    continue
                currTokens,currTypes = tokenstr
                words = set(token for j,token in enumerate(currTokens) if currTypes[j] in (2,3))
                leftTrimStop = rightTrimStop = False
                for w in self._cls2wordpunSorted[cls]:
                    if not w in words:
                        continue
                    newTokens,newTypes = self._abstract(w,currTokens,currTypes,maxSpaceLen,maxWordLen)
                    regex = self._getRegex((newTokens,newTypes))
                    if matchOppOf(regex,cls):
                        actions.append(w+':F')
                        break
                    actions.append(w+':S')
                    currTokens,currTypes = newTokens,newTypes
                    nTokens = self._nLeftTrim(currTypes)
                    if not leftTrimStop and nTokens>0:
                        newTokens,newTypes = currTokens[nTokens:],currTypes[nTokens:]
                        regex = self._getRegex((newTokens,newTypes))
                        leftTrimStop = matchOppOf(regex,cls)
                        if not leftTrimStop:
                            actions.append('left-trim:S')
                            currTokens,currTypes = newTokens,newTypes
                        else:
                            actions.append('left-trim:F')
                    nTokens = self._nRightTrim(currTypes)
                    if not rightTrimStop and nTokens>0:
                        newTokens,newTypes = currTokens[:-nTokens],currTypes[:-nTokens]
                        regex = self._getRegex((newTokens,newTypes))
                        rightTrimStop = matchOppOf(regex,cls)
                        if not rightTrimStop:
                            actions.append('right-trim:S')
                            currTokens,currTypes = newTokens,newTypes
                        else:
                            actions.append('right-trim:F')
                n = 0
                while n<100 and not (leftTrimStop and rightTrimStop):
                    n += 1
                    if not leftTrimStop:
                        nTokens = self._nLeftTrim(currTypes)
                        leftTrimStop = (nTokens==0)
                        if not leftTrimStop:
                            newTokens,newTypes = currTokens[nTokens:],currTypes[nTokens:]
                            regex = self._getRegex((newTokens,newTypes))
                            leftTrimStop = matchOppOf(regex,cls)
                            if not leftTrimStop:
                                actions.append('left-trim:S')
                                currTokens,currTypes = newTokens,newTypes
                            else:
                                actions.append('left-trim:F')
                    if not rightTrimStop:
                        nTokens = self._nRightTrim(currTypes)
                        rightTrimStop = (nTokens==0)
                        if not rightTrimStop:
                            newTokens,newTypes = currTokens[:-nTokens],currTypes[:-nTokens]
                            regex = self._getRegex((newTokens,newTypes))
                            rightTrimStop = matchOppOf(regex,cls)
                            if not rightTrimStop:
                                actions.append('right-trim:S')
                                currTokens,currTypes = newTokens,newTypes
                            else:
                                actions.append('right-trim:F')
                regex = self._getRegex((currTokens,currTypes))
                if regex in unqRegex2idStrict:
                    idStrict2idInits[unqRegex2idStrict[regex]].append(idx)
                    self._cls2initActions[cls].append(actions)
                    continue # continue on for idx,tokenstr in ...
                unqRegex2idStrict[regex] = len(strictTokenstrs)
                idStrict2idInits[len(strictTokenstrs)] = [idx]
                strictTokenstrs.append((currTokens,currTypes))
                self._cls2initActions[cls].append(actions)
            print len(strictTokenstrs)
            self._cls2strictTokenstrs[cls] = strictTokenstrs
            self._cls2idStrict2idInits[cls] = idStrict2idInits

    def _calcCounts(self):
        for cls in self._cls2strictTokenstrs:
            counts = []
            for tokenstr in self._cls2strictTokenstrs[cls]:
                regex = self._getRegex(tokenstr)
                ptn = re.compile(regex,re.I)
                count = 0
                for snippet,start,end in self._cls2snippets_segspans[cls]:
                    if re.search(ptn,snippet):
                        count += 1
                counts.append(count)
            self._cls2strictCounts[cls] = counts
            
    def _calcCountsForRejected(self):
        for cls in self._cls2initRejected:
            self._cls2rejected_cls2count[cls] = []
            for i in self._cls2initRejected[cls]:
                regex = self._getRegex(self._cls2initTokenstrs[cls][i])
                cls2idxsMatched = self._calcMatches(regex)
                cls2count = {c:len(cls2idxsMatched[c]) for c in cls2idxsMatched}
                self._cls2rejected_cls2count[cls].append(cls2count)
            
    def _sortStrict(self):
        for cls in self._cls2strictCounts:
            lenStrict = len(self._cls2strictCounts[cls])
            self._cls2idStrictSorted[cls] = sorted(range(lenStrict),
                        key=lambda i:-self._cls2strictCounts[cls][i])

    def _calcMatches(self,regex):
        ptn = re.compile(regex,re.I)
        cls2idxsMatched = {}
        cls2snip_spans = self._cls2snippets_segspans
        for c in cls2snip_spans:
            cls2idxsMatched[c] = set()
            for i,(snippet,start,end) in enumerate(cls2snip_spans[c]):
                if re.search(ptn,snippet):
                    cls2idxsMatched[c].add(i)
        return cls2idxsMatched

    def _abstractTrimTest(self,tokenstr,cls,alpha,actions):
        def likelyhoodTest(cls2idxsMatched):
            likelyhood = len(cls2idxsMatched[cls])*1.0/lenCls
            likelyhoodOpp = sum(len(cls2idxsMatched[c])
                            for c in cls2snip_spans if c!=cls)*1.0/lenOpp
            return (likelyhoodOpp<alpha*likelyhood)
        maxSpaceLen = self._cls2maxSpaceLen[cls]
        maxWordLen = self._cls2maxWordLen[cls]
        cls2snip_spans = self._cls2snippets_segspans
        lenCls = len(cls2snip_spans[cls])
        lenOpp = sum(len(cls2snip_spans[c]) for
                        c in cls2snip_spans if c!=cls)
        cls2idxsMatched = {}
        currTokens,currTypes = tokenstr
        words = set(token for j,token in enumerate(currTokens) if currTypes[j] in (2,3))
        for w in self._cls2wordpunSorted[cls]:
            if not w in words:
                continue
            newTokens,newTypes = self._abstract(w,currTokens,currTypes,maxSpaceLen,maxWordLen)
            regex = self._getRegex((newTokens,newTypes))
            cls2idxsNewMatched = self._calcMatches(regex)
            if not likelyhoodTest(cls2idxsNewMatched):
                actions.append(w+':F')
                break
            currTokens,currTypes = newTokens,newTypes
            cls2idxsMatched = cls2idxsNewMatched
            actions.append(w+':S')
            nTokens = self._nLeftTrim(currTypes)
            if nTokens>0:
                newTokens,newTypes = currTokens[nTokens:],currTypes[nTokens:]
                regex = self._getRegex((newTokens,newTypes))
                cls2idxsNewMatched = self._calcMatches(regex)
                if likelyhoodTest(cls2idxsNewMatched):
                    currTokens,currTypes = newTokens,newTypes
                    cls2idxsMatched = cls2idxsNewMatched
                    actions.append('left-trim:S')
                else: actions.append('left-trim:F')
            nTokens = self._nRightTrim(currTypes)
            if nTokens>0:
                newTokens,newTypes = currTokens[:-nTokens],currTypes[:-nTokens]
                regex = self._getRegex((newTokens,newTypes))
                cls2idxsNewMatched = self._calcMatches(regex)
                if likelyhoodTest(cls2idxsNewMatched):
                    currTokens,currTypes = newTokens,newTypes
                    cls2idxsMatched = cls2idxsNewMatched
                    actions.append('right-trim:S')
                else: actions.append('right-trim:F')
        n = 0
        leftTrimStop = rightTrimStop = False
        while n<100 and not (leftTrimStop and rightTrimStop):
            n += 1
            if not leftTrimStop:
                nTokens = self._nLeftTrim(currTypes)
                leftTrimStop = (nTokens==0)
                if not leftTrimStop:
                    newTokens,newTypes = currTokens[nTokens:],currTypes[nTokens:]
                    regex = self._getRegex((newTokens,newTypes))
                    cls2idxsNewMatched = self._calcMatches(regex)
                    leftTrimStop = not likelyhoodTest(cls2idxsNewMatched)
                    if not leftTrimStop:
                        currTokens,currTypes = newTokens,newTypes
                        cls2idxsMatched = cls2idxsNewMatched
                        actions.append('left-trim:S')
                    else: actions.append('left-trim:F')
            if not rightTrimStop:
                nTokens = self._nRightTrim(currTypes)
                rightTrimStop = (nTokens==0)
                if not rightTrimStop:
                    newTokens,newTypes = currTokens[:-nTokens],currTypes[:-nTokens]
                    regex = self._getRegex((newTokens,newTypes))
                    cls2idxsNewMatched = self._calcMatches(regex)
                    rightTrimStop = not likelyhoodTest(cls2idxsNewMatched)
                    if not rightTrimStop:
                        currTokens,currTypes = newTokens,newTypes
                        cls2idxsMatched = cls2idxsNewMatched
                        actions.append('right-trim:S')
                    else: actions.append('right-trim:S')
        cls2count = {c:len(cls2idxsMatched[c]) for c in cls2idxsMatched}
        return (currTokens,currTypes),cls2count
    
    def _generalize1(self):
        alpha = 0.1
        for cls in self._cls2strictTokenstrs:
            print 'for class:',cls
            self._cls2strictActions[cls] = []
            self._cls2strictOverFailed[cls] = []
            unqOver2idOver = {}
            overTokenstrs = []
            idOver2idStricts = {}
            strictActions = []
            idOver2cls2count = {}
            for idx,tokenstr in enumerate(self._cls2strictTokenstrs[cls]):
                progress = idx+1
                if progress%10==0:
                    print '.',
                if progress%200==0:
                    print
                actions = []
                strictRegex = self._getRegex(tokenstr)
                overTokenstr,cls2count = self._abstractTrimTest(tokenstr,cls,alpha,actions)
                strictActions.append(actions)
                regex = self._getRegex(overTokenstr)
                if regex==strictRegex:
                    self._cls2strictOverFailed[cls].append(idx)
                    continue
                if regex in unqOver2idOver:
                    idOver2idStricts[unqOver2idOver[regex]].append(idx)
                    continue
                unqOver2idOver[regex] = len(overTokenstrs)
                idOver2idStricts[len(overTokenstrs)] = [idx]
                idOver2cls2count[len(overTokenstrs)] = cls2count
                overTokenstrs.append(overTokenstr)
            print len(overTokenstrs)
            self._cls2overTokenstrs[cls] = overTokenstrs
            self._cls2idOver2idStricts[cls] = idOver2idStricts
            self._cls2strictActions[cls] = strictActions
            self._cls2idOver2cls2count[cls] = idOver2cls2count
            
    def _sortOver(self):
        clsid2lhratio = {}
        cls2snip_spans = self._cls2snippets_segspans
        for cls in self._cls2idOver2cls2count:
            lenCls = len(cls2snip_spans[cls])
            lenOpp = sum(len(cls2snip_spans[c]) for
                            c in cls2snip_spans if c!=cls)
            for i in self._cls2idOver2cls2count[cls]:
                cls2count = self._cls2idOver2cls2count[cls][i]
                likelyhood = cls2count[cls]*1.0/lenCls
                countOpp = sum(cls2count[c] for c in cls2snip_spans if c!=cls)
                if countOpp==0:
                    countOpp = 1 #solution for the moment. need to be fixed.
                likelyhoodOpp = countOpp*1.0/lenOpp
                clsid2lhratio[(cls,i)] = likelyhood/likelyhoodOpp
        self._clsIdOverSorted = sorted(clsid2lhratio,key=lambda ci:-clsid2lhratio[ci])
        
    def _generalize30(self):
        alpha = 0.3
        for cls in self._cls2strictOverFailed:
            print 'for class:',cls
            over3Tokenstrs = []
            unqOver32idOver3 = {}
            idOver32cls2count = {}
            idOver32idStricts = {}
            unqOver = set(self._getRegex(t) for t in self._cls2overTokenstrs[cls])
            for idx,i in enumerate(self._cls2strictOverFailed[cls]):
                if (idx+1)%10==0:
                    print '.',
                if (idx+1)%200==0:
                    print
                actions = []
                tokenstr = self._cls2strictTokenstrs[cls][i]
                strictRegex = self._getRegex(tokenstr)
                over3Tokenstr,cls2count = self._abstractTrimTest(tokenstr,cls,alpha,actions)
                regex = self._getRegex(over3Tokenstr)
                if regex==strictRegex:
                    continue
                if regex in unqOver:
                    continue
                if regex in unqOver32idOver3:
                    idOver32idStricts[unqOver32idOver3[regex]].append(i)
                    continue
                idOver32idStricts[len(over3Tokenstrs)] = [i]
                unqOver32idOver3[regex] = len(over3Tokenstrs)
                idOver32cls2count[len(over3Tokenstrs)] = cls2count
                over3Tokenstrs.append(over3Tokenstr)
            self._cls2over30Tokenstrs[cls] = over3Tokenstrs
            self._cls2idOver302cls2count[cls] = idOver32cls2count
            self._cls2idOver302idStricts[cls] = idOver32idStricts
            print len(over3Tokenstrs)
            
    def _sortOver30(self):
        clsid2lhratio = {}
        cls2snip_spans = self._cls2snippets_segspans
        for cls in self._cls2idOver302cls2count:
            lenCls = len(cls2snip_spans[cls])
            lenOpp = sum(len(cls2snip_spans[c]) for
                            c in cls2snip_spans if c!=cls)
            for i in self._cls2idOver302cls2count[cls]:
                cls2count = self._cls2idOver302cls2count[cls][i]
                likelyhood = cls2count[cls]*1.0/lenCls
                countOpp = sum(cls2count[c] for c in cls2snip_spans if c!=cls)
                if countOpp==0:
                    countOpp = 1 #solution for the moment. needs to be fixed.
                likelyhoodOpp = countOpp*1.0/lenOpp
                clsid2lhratio[(cls,i)] = likelyhood/likelyhoodOpp
        self._clsIdOver30Sorted = sorted(clsid2lhratio,key=lambda ci:-clsid2lhratio[ci])
     
                
    def _generalize31(self):
        alpha = 0.3
        for cls in self._cls2overTokenstrs:
            over3Tokenstrs = []
            unqOver32idOver3 = {}
            idOver32cls2count = {}
            unqOver = set(self._getRegex(t) for t in self._cls2overTokenstrs[cls])
            for idx,tokenstr in enumerate(self._cls2overTokenstrs[cls]):
                if (idx+1)%10==0:
                    print '.',
                if (idx+1)%200==0:
                    print
                actions = []
                over3Tokenstr,cls2count = self._abstractTrimTest(tokenstr,cls,alpha,actions)
                regex = self._getRegex(over3Tokenstr)
                if regex in unqOver32idOver3:
                    continue
                if regex in unqOver:
                    continue
                unqOver32idOver3[regex] = len(over3Tokenstrs)
                idOver32cls2count[len(over3Tokenstrs)] = cls2count
                over3Tokenstrs.append(over3Tokenstr)
            self._cls2over31Tokenstrs[cls] = over3Tokenstrs
            self._cls2idOver312cls2count[cls] = idOver32cls2count
            print len(over3Tokenstrs)
        
    def _predict0(self,snippets):
        idSnippet2cls2idStrictSpanSen = {}
        for cls in self._cls2idStrictSorted:
            lenTrain = len(self._cls2snippets_segspans[cls])
            for i in self._cls2idStrictSorted[cls]:
                count = self._cls2strictCounts[cls][i]
                sen = count*100.0/lenTrain
                regex = self._getRegex(self._cls2strictTokenstrs[cls][i])
                ptn = re.compile(regex,re.I)
                for j,snippet in enumerate(snippets):
                    if j in idSnippet2cls2idStrictSpanSen and cls in idSnippet2cls2idStrictSpanSen[j]:
                        continue
                    m = re.search(ptn,snippet)
                    if m:
                        if not j in idSnippet2cls2idStrictSpanSen:
                            idSnippet2cls2idStrictSpanSen[j] = {}
                        idSnippet2cls2idStrictSpanSen[j][cls] = (i,m.span(),sen)
        self._idTest2cls2idStrictSpanSen = idSnippet2cls2idStrictSpanSen
        preds = []
        for j in xrange(len(snippets)):
            if not j in idSnippet2cls2idStrictSpanSen:
                preds.append(None)
                continue
            cls2idStrictSpanSen = idSnippet2cls2idStrictSpanSen[j]
            cls = max(cls2idStrictSpanSen,key=lambda c:cls2idStrictSpanSen[c][-1])
            preds.append(cls)
        return preds
        
    def _predict1(self,snippets):
        idSnippet2cls2idOverSpanLR = {}
        cls2snip_spans = self._cls2snippets_segspans
        for cls,i in self._clsIdOverSorted:
            lenCls = len(cls2snip_spans[cls])
            lenOpp = sum(len(cls2snip_spans[c]) for
                            c in cls2snip_spans if c!=cls)
            cls2count = self._cls2idOver2cls2count[cls][i]
            lh = cls2count[cls]*1.0/lenCls
            ctOpp = sum(cls2count[c] for c in cls2snip_spans if c!=cls)
            if ctOpp==0:
                ctOpp = 1 #solution for the moment. needs to be fixed.
            lhOpp = ctOpp*1.0/lenOpp
            lhratio = lh/lhOpp
            regex = self._getRegex(self._cls2overTokenstrs[cls][i])
            ptn = re.compile(regex,re.I)
            for j,snippet in enumerate(snippets):
                if j in idSnippet2cls2idOverSpanLR and cls in idSnippet2cls2idOverSpanLR[j]:
                    continue
                m = re.search(ptn,snippet)
                if m:
                    if not j in idSnippet2cls2idOverSpanLR:
                        idSnippet2cls2idOverSpanLR[j] = {}
                    idSnippet2cls2idOverSpanLR[j][cls] = (i,m.span(),lhratio)
        self._idTest2cls2idOverSpanLR = idSnippet2cls2idOverSpanLR
        preds1 = []
        for j in xrange(len(snippets)):
            if not j in idSnippet2cls2idOverSpanLR:
                preds1.append(None)
                continue
            cls2idOverSpanLR = idSnippet2cls2idOverSpanLR[j]
            cls = max(cls2idOverSpanLR,key=lambda c:cls2idOverSpanLR[c][-1])
            preds1.append(cls)
        return preds1
        
    def _predict3(self,snippets):
        idSnippet2cls2idOver3SpanLR = {}
        cls2snip_spans = self._cls2snippets_segspans
        for cls,i in self._clsIdOver30Sorted:
            lenCls = len(cls2snip_spans[cls])
            lenOpp = sum(len(cls2snip_spans[c]) for
                            c in cls2snip_spans if c!=cls)
            cls2count = self._cls2idOver302cls2count[cls][i]
            lh = cls2count[cls]*1.0/lenCls
            ctOpp = sum(cls2count[c] for c in cls2snip_spans if c!=cls)
            if ctOpp==0:
                ctOpp = 1 #solution for the moment. needs to be fixed.
            lhOpp = ctOpp*1.0/lenOpp
            lhratio = lh/lhOpp
            regex = self._getRegex(self._cls2over30Tokenstrs[cls][i])
            ptn = re.compile(regex,re.I)
            for j,snippet in enumerate(snippets):
                if j in idSnippet2cls2idOver3SpanLR and cls in idSnippet2cls2idOver3SpanLR[j]:
                    continue
                m = re.search(ptn,snippet)
                if m:
                    if not j in idSnippet2cls2idOver3SpanLR:
                        idSnippet2cls2idOver3SpanLR[j] = {}
                    idSnippet2cls2idOver3SpanLR[j][cls] = (i,m.span(),lhratio)
        self._idTest2cls2idOver3SpanLR = idSnippet2cls2idOver3SpanLR
        preds3 = []
        for j in xrange(len(snippets)):
            if not j in idSnippet2cls2idOver3SpanLR:
                preds3.append(None)
                continue
            cls2lhratio = idSnippet2cls2idOver3SpanLR[j]
            cls = max(cls2lhratio,key=cls2lhratio.get)
            preds3.append(cls)
        return preds3