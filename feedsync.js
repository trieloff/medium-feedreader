//HELLO!


var request = require('request');

var Medium = function(apikey, cb) {
  console.log("Creating Medium");
  this.key = apikey;
  this.cb = cb;
  
  this.publicationUrls = function(publication) {
    return publication.url;
  }
  
  this.fulltextFeeds = function(mediumUrl) {
    return "https://www.freefullrss.com/feed.php?url=" + mediumUrl.replace(/medium\.com\//, "medium.com/feed/");
  }
  
  this.getPubs = function() {
    console.log("Getting Medium publications");
    var medium = this;
    request("https://api.medium.com/v1/users/" + medium.user.id + "/publications", function(error, response, body) {
      if (!error && response.statusCode == 200) {
        var publications = JSON.parse(body);
        medium.publications = publications.data.map(medium.publicationUrls).map(medium.fulltextFeeds);
        console.log(medium.publications.length + " Medium publications retrieved");
        medium.cb(medium);
      } else {
        console.log("Could not retrieve Medium publications: " + error);
      }
    }).auth(null, null, true, this.key);
  }
  
  this.init = function() {
    var medium = this;
    request('https://api.medium.com/v1/me', function (error, response, body) {
    if (!error && response.statusCode == 200) {
      var me = JSON.parse(body);
      medium.user = me.data;
      console.log("Medium user initialized");
      medium.getPubs();
    } else {
      console.log("Could not retrieve Medium user: " + error);
    }}).auth(null, null, true, this.key);
  }
  
  this.init();
}

var Feedbin = function(email, password, medium, cb) {
  console.log("Creating Feedbin");
  this.email = email;
  this.password = password;
  this.cb = cb;
  this.medium = medium;
  
  var feedbin = this;
  
  this.subscriptionUrls = function(subscription) {
    return subscription.feed_url;
  }
  
  this.mediumSubscriptions = function(subscription) {
    return (subscription.feed_url.match(/medium.com/)!==null);
  }
  
  this.fulltextFeeds = function(mediumUrl) {
    return "https://www.freefullrss.com/feed.php?url=" + mediumUrl.replace(/medium\.com\//, "medium.com/feed/");
  }
  
  this.subscribe = function(url) {
    console.log("Subscribing to " + url);
    
    var feedbin = this;
    
    console.log("Username: " + feedbin.email);
    
    request({
      "url": "https://api.feedbin.com/v2/subscriptions.json",
      "method": "POST",
      "json": { "feed_url": url},
      "auth": {
        "user": feedbin.email,
        "pass": feedbin.password,
        "sendImmediately": true
      }
    }, function(error, response, body) {
      if (!error && response.statusCode == 201) {
        console.log("Subscribed to " + url);
      } else {
        console.log("Could not subscribe to " + url + ": " + error + " " + response.statusCode);
      }
    });
  }
  
  this.unsubscribe = function(url) {
    console.log("Unsubscribing from " + url);
    
    var filtered = this.subscriptions.filter(function(sub){
      return sub.feed_url == url;
    });
    
    console.log("Found " + filtered.length + " subscriptions with matching URL");
    
    var feedbin = this;
    
    for (var i = 0; i < filtered.length; i++) {
      console.log("Deleting subscription https://api.feedbin.com/v2/subscriptions/" + filtered[i].id + ".json");
      
      request({
        "url": "https://api.feedbin.com/v2/subscriptions/" + filtered[i].id + ".json",
        "method": "DELETE",
        "auth": {
          "user": feedbin.email,
          "pass": feedbin.password,
          "sendImmediately": true
        }
      }, function(error, response, body) {
        if (!error && response.statusCode == 204) {
          console.log("Unsubscribed from " + url);
        } else {
          console.log("Could not unsubscribe from " + url + ": " + error + " " + response.statusCode);
        }
      });
    }
  }
  
  request("https://api.feedbin.com/v2/subscriptions.json", function(error, response, body) {
    if (!error && response.statusCode == 200) {
      feedbin.subscriptions = JSON.parse(body);
      console.log("Feedbin subscriptions initialized");
      
      feedbin.mediumFeeds = feedbin.subscriptions.filter(feedbin.mediumSubscriptions).map(feedbin.subscriptionUrls);
      
      var unsubscribeFrom = difference(feedbin.mediumFeeds, feedbin.medium.publications);
      var subscribeTo = difference(feedbin.medium.publications, feedbin.mediumFeeds);
      
      console.log("Found " + feedbin.mediumFeeds.length + " Medium feeds in Feedbin subscriptions. Subscribing to " + subscribeTo.length + " feeds. Unsubscribing from " + unsubscribeFrom.length + " feeds.");
      
      for (var i = 0;i < subscribeTo.length; i++) {
        feedbin.subscribe(subscribeTo[i]);
      }
      
      for (var i = 0;i < unsubscribeFrom.length; i++) {
        feedbin.unsubscribe(unsubscribeFrom[i]);
      }
      
      
      feedbin.cb();
    } else {
      console.log("Could not retrieve Feedbin subscriptions");
    }
  }).auth(this.email, this.password, true);
}

var intersection = function(array1, array2) {
  return array1.filter(function(n) {
    return array2.indexOf(n) != -1;
  });
}

var difference = function(array1, array2) {
  console.log("Getting difference " + array1.length + " " + array2.length);
  return array1.filter(function(n) {
    return array2.indexOf(n) == -1;
  });
}

module.exports = function (context, cb) {
  var medium = new Medium(context.secrets.MEDIUM, function(medium) {    
    //cb(null, "Medium is done here.");
    var feedbin = new Feedbin(context.secrets.FEEDBINEMAIL, context.secrets.FEEDBINPASSWORD, medium, function() {
      cb(null, "Feedbin is done here.");
    });
    
  });
}
