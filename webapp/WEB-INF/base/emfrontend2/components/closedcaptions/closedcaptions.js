var initclosedcaptions = function() 
{
	console.log("Closed Caption init");
	
	var app = $("#application");
	var apphome = app.data("siteroot") + app.data("apphome");
	var themeprefix = app.data("siteroot")	+ app.data("themeprefix");
	
	var videoclip = $("#videoclipcc");
	var video = document.getElementById("videoclipcc");//videoclip[0];
	
	if (video == null) {
		return;
	}
	
	
	var inTime = video.currentTime;
	
	parseTimeToText = function(inTime)
	{
			var justseconds = Math.floor(inTime);
			var justremainder = inTime - justseconds;			
			var millis = Math.floor(justremainder * 1000);
			
			var minutes = Math.floor(justseconds / 60);
			var m = zeroPad(minutes,2);

			var secondsleft = justseconds - (minutes*60);
			var s = zeroPad(secondsleft,2);
			var done = m + ":" + s + "." + millis;
			return done;
	}
	zeroPad = function(num, numZeros) 
	{
	    var n = Math.abs(num);
	    var zeros = Math.max(0, numZeros - Math.floor(n).toString().length );
	    var zeroString = Math.pow(10,zeros).toString().substr(1);
	    if( num < 0 ) {
	        zeroString = '-' + zeroString;
	    }

	    return zeroString+n;
	}
	
	
	$("#captionend").text(parseTimeToText(inTime));
	
	var link = $("#playtab");
	
	var starttime = 0;
	
	

	
	startchunk = function()
	{
	    video.play();
	    link.addClass("playing");
	    link.text(link.data("stoptext"));
	    $("#captioninput").focus();
	};
	
	stopchunk = function()
	{
		video.pause();
		link.removeClass("playing");
	    link.text(link.data("playtext"));
	    $("#captioninput").focus();
	};			    

	saveCaptionToServer = function()
	{
		//update the data
//		var starttime = video.currentTime;
//		$("#captionstart").text(parseTimeToText(starttime));
//		$("#timecodestart").val( Math.round(starttime * 1000 ) );
	
		$("#addcaption").ajaxSubmit({
			target:"#captionview",
			error: function(data)
			{
				$("#captionview").html(data);
			},
			success: function() 
			{
				$("#captioninput").val("");
				$("#scrollarea").scrollTop($("#scrollarea")[0].scrollHeight);
				var endingtime = video.currentTime + 8;
				$("#timecodelength").val(8000);
				$("#captionend").text(parseTimeToText(endingtime));
			}
	 	});
	}	

	//Closed caption stuff
	lQuery("#captioninput").livequery( "keydown",function(e) 
	{
			var theinput = $(this);
			
			var keyCode = e.keyCode || e.which; 

			if (keyCode == 9) //tab
			{ 
			 	e.preventDefault(); 
				if( video.paused )
				{
					startchunk();
				}
				else
				{
				    stopchunk();
				}
			} 
			else if (keyCode == 13) //enter
			{ 
				saveCaptionToServer();
			}
	});
	lQuery("#addcaption").livequery('submit',function(e)
	{ 
		e.preventDefault();
	 	return false;
	});
	
	lQuery("#playtab").livequery("click",function(e)
	{
		e.preventDefault();
		if(link.hasClass("playing") )
		{
		    stopchunk();
		}
		else
		{
			startchunk();
		}
		
	});
	
	lQuery(".lenguagepicker").livequery("change",function(e)
	{
		var selected = $(this);
		$("#langform").submit();
	});
	
	videoclip.on("timeupdate",function(e) {
		debugger;
		var link = $("#playtab");
		if( video.paused )
		{
			link.removeClass("playing");
			starttime = video.currentTime;
			updateCaptions(starttime);
		}
		else if(link.hasClass("playing") )
		{
			 var inTime = video.currentTime;
			 if( inTime > starttime  + 8 )
			 {
			 	stopchunk();
			 }
		 	var inTime = video.currentTime + 8;
			$("#captionend").text(parseTimeToText(inTime));
		}
	});	
	
	lQuery("#removetime").livequery("click",function(e)
	{
		e.preventDefault();
		var link = $(this);
		video.currentTime = video.currentTime - .5;
		return false;
	});
	
	lQuery("#addtime").livequery("click",function(e)
	{
		e.preventDefault();
		var link = $(this);
		video.currentTime = video.currentTime + .5;
		return false;
	});
	lQuery("#removecaption").livequery("click",function(e)
	{
		e.preventDefault();
		var link = $(this);
		
		$("#captioninput").val("");
		
		$("#addcaption").ajaxSubmit({
			target:"#captionview",
			error: function(data)
			{
				$("#captionview").html(data);
			},
			success: function() 
			{
			}
	 	});
		
		return false;
	});

	ccselectClip = function(div)
	{
		var div = $(div).closest(".cc-data-selection");
		$(".cc-data-selection").removeClass("selectedclip");
		div.addClass("selectedclip");
		ccupdateDetails();
	}
	
	ccupdateDetails = function(jumptoend)
	{
		var selected = $(".selectedclip");
	
		$("#captioninput").val( selected.data("cliplabel") );
		var decstart = selected.data("timecodestart");
		decstart = parseFloat(decstart);
		if( decstart )
		{
			video.currentTime = decstart / 1000;
		}
		var declength = selected.data("timecodelength");
		declength = parseFloat(declength);
		$("#timecodelength").val(declength);
		var ending = (decstart + declength);
		$("#captionend").text(parseTimeToText(ending/1000));
		
	}
	
	ccupdatecaptions = function(starttime) {
		$("#captionstart").text(parseTimeToText(starttime));
		$("#timecodestart").val( Math.round(starttime * 1000 ));
	}
	
	lQuery(".cc-data-selection").livequery("click", function(e)	{
		e.preventDefault();
		ccselectClip(this);
		ccupdateDetails();
		ccupdatecaptions(video.currentTime);
	});	
};



jQuery(document).ready(function()
{
	initclosedcaptions();
	
	$(window).on('tabready',function()
	{
		initclosedcaptions();
	});
});



